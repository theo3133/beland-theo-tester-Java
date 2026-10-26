package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    private static void setUp() throws Exception{
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    private static void tearDown(){

    }

    @Test
    public void testParkingACar() throws Exception {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();

        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket);
        assertEquals("ABCDEF", ticket.getVehicleRegNumber());

        try (Connection connection = dataBaseTestConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT AVAILABLE FROM parking WHERE PARKING_NUMBER = ?")) {
            statement.setInt(1, ticket.getParkingSpot().getId());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertFalse(result.getBoolean("AVAILABLE"));
            }
        }
    }

    @Test
    public void testParkingLotExit() throws Exception {
        // GIVEN
        testParkingACar();

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket);

        Date inTime = new Date();
        inTime.setTime(System.currentTimeMillis() - (  40 * 60 * 1000) );

        try (Connection connection = dataBaseTestConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ticket SET IN_TIME = ? WHERE ID = ?")) {

            statement.setTimestamp(1, new Timestamp(inTime.getTime()));
            statement.setInt(2, ticket.getId());

            assertEquals(1, statement.executeUpdate());
        }

        // WHEN
        parkingService.processExitingVehicle();

        // THEN
        ticket = ticketDAO.getTicket("ABCDEF");

        assertNotNull(ticket);
        assertNotNull(ticket.getOutTime());

        double prixAttendu = (40.0 / 60) * Fare.CAR_RATE_PER_HOUR;
        assertEquals(prixAttendu, ticket.getPrice(), 0.01);


        try (Connection connection = dataBaseTestConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT AVAILABLE FROM parking WHERE PARKING_NUMBER = ?")) {

            statement.setInt(1, ticket.getParkingSpot().getId());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getBoolean("AVAILABLE"));
            }
        }
    }

    @Test
    public void testParkingLotExitRecurringUser() throws Exception{
        // GIVEN
        testParkingLotExit();
        testParkingACar();
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        Ticket ticket = ticketDAO.getTicket("ABCDEF"); // rangé dans l ordre decroissant donc déjà trié
        assertNotNull(ticket);

        Date inTime = new Date();
        inTime.setTime(System.currentTimeMillis() - (  40 * 60 * 1000) );

        try (Connection connection = dataBaseTestConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ticket SET IN_TIME = ? WHERE ID = ?")) {

            statement.setTimestamp(1, new Timestamp(inTime.getTime()));
            statement.setInt(2, ticket.getId());
            assertEquals(1, statement.executeUpdate());
        }
        // WHEN
        parkingService.processExitingVehicle();

        //then

        ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket);
        assertNotNull(ticket.getOutTime());
        double prixAttendu = (40.0 / 60) * Fare.CAR_RATE_PER_HOUR*0.95;
        assertEquals(prixAttendu, ticket.getPrice(), 0.01);

    }

}
