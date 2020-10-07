package flightapp;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  private boolean login = false;
  private String user;
  private List<Itinerary> output = new ArrayList<>();

  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  // For check dangling
  private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
  private PreparedStatement tranCountStatement;
  private PreparedStatement checkUsernameStatement;
  private PreparedStatement insertUserStatement;
  private PreparedStatement checkSameDay;
  private PreparedStatement getCapacity;
  private PreparedStatement insertCapacity;
  private PreparedStatement insertReservation;
  private PreparedStatement getRid;
  private PreparedStatement getReservation;
  private PreparedStatement updateBalance;
  private PreparedStatement updateReservation;
  private PreparedStatement listValidReservation;
  private PreparedStatement getFlightInfo;
  private PreparedStatement listSingleValidReservation;
  private PreparedStatement updateCapacity;
  private PreparedStatement deleteReservation;
  // TODO: YOUR CODE HERE

  public Query() throws SQLException, IOException {
    this(null, null, null, null);
  }

  protected Query(String serverURL, String dbName, String adminName, String password)
      throws SQLException, IOException {
    conn = serverURL == null ? openConnectionFromDbConn()
        : openConnectionFromCredential(serverURL, dbName, adminName, password);

    prepareStatements();
  }

  /**
   * Return a connecion by using dbconn.properties file
   *
   * @throws SQLException
   * @throws IOException
   */
  public static Connection openConnectionFromDbConn() throws SQLException, IOException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("flightapp.server_url");
    String dbName = configProps.getProperty("flightapp.database_name");
    String adminName = configProps.getProperty("flightapp.username");
    String password = configProps.getProperty("flightapp.password");
    return openConnectionFromCredential(serverURL, dbName, adminName, password);
  }

  /**
   * Return a connecion by using the provided parameter.
   *
   * @param serverURL example: example.database.widows.net
   * @param dbName    database name
   * @param adminName username to login server
   * @param password  password to login server
   *
   * @throws SQLException
   */
  protected static Connection openConnectionFromCredential(String serverURL, String dbName,
      String adminName, String password) throws SQLException {
    String connectionUrl =
        String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
            dbName, adminName, password);
    Connection conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

    return conn;
  }

  /**
   * Get underlying connection
   */
  public Connection getConnection() {
    return conn;
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      Statement statement = conn.createStatement();
      statement.executeUpdate("ALTER TABLE reservations\n" +
              "DROP CONSTRAINT FK_username");
      statement.executeUpdate("TRUNCATE TABLE users");
      statement.executeUpdate("TRUNCATE TABLE reservations");
      statement.executeUpdate("TRUNCATE TABLE capacity");
      statement.executeUpdate("ALTER TABLE reservations\n" +
              "ADD CONSTRAINT FK_username\n" +
              "FOREIGN KEY (username) REFERENCES users(username)");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
    checkUsernameStatement = conn.prepareStatement("SELECT * FROM USERS WHERE Username = ?");
    insertUserStatement = conn.prepareStatement("INSERT INTO users VALUES(?,?,?,?)");
    checkSameDay = conn.prepareStatement("SELECT * FROM RESERVATIONS AS R, FLIGHTS AS F WHERE F.fid = R.fid1 and R.username = ? AND F.day_of_month = ?");
    getCapacity = conn.prepareStatement("SELECT * FROM CAPACITY AS CAP WHERE CAP.FID = ?");
    insertCapacity = conn.prepareStatement("INSERT INTO capacity VALUES(?,?)");
    insertReservation = conn.prepareStatement("INSERT INTO RESERVATIONS VALUES(?,?,?,?,?,?)");
    getRid = conn.prepareStatement("SELECT MAX(R.rid) as rid FROM RESERVATIONS AS R");
    getReservation = conn.prepareStatement("SELECT * FROM RESERVATIONS WHERE rid = ? AND username = ? AND paid = ?");
    listValidReservation = conn.prepareStatement("SELECT * FROM RESERVATIONS WHERE username = ? AND canceled = 0");
    updateBalance = conn.prepareStatement("UPDATE USERS SET balance = ? Where username = ?");
    updateReservation = conn.prepareStatement("UPDATE RESERVATIONS SET PAID = 1 WHERE rid = ?");
    getFlightInfo = conn.prepareStatement("SELECT * FROM Flights WHERE fid = ?");
    listSingleValidReservation = conn.prepareStatement("SELECT * FROM RESERVATIONS WHERE username = ? AND canceled = 0 AND rid = ?");
    updateCapacity = conn.prepareStatement("UPDATE capacity set freeSeat = ? where fid = ?");
    deleteReservation = conn.prepareStatement("DELETE FROM RESERVATIONS WHERE rid = ?");
    // TODO: YOUR CODE HERE
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    try {
      // TODO: YOUR CODE HERE
      if(login) {
        return "User already logged in\n";
      }
      checkUsernameStatement.clearParameters();
      checkUsernameStatement.setString(1, username);      // Sets the first parameter (the first “?”) to the value of the variable “originCity”
      ResultSet rs = checkUsernameStatement.executeQuery();
      if(rs.next()) {
        String getUser = rs.getString("username");
        byte[] getSalt = rs.getBytes("salt");
        byte[] getHash = rs.getBytes("hash");

        // Specify the hash parameters
        KeySpec spec = new PBEKeySpec(password.toCharArray(), getSalt, HASH_STRENGTH, KEY_LENGTH);

        // Generate the hash
        SecretKeyFactory factory = null;
        byte[] hash = null;
        try {
          factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
          hash = factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
          throw new IllegalStateException();
        }

        if(Arrays.equals(getHash, hash)){
          login = true;
          user = getUser;
          return ("Logged in as " + getUser + "\n");
        }
      }
      return "Login failed\n";
    } catch(SQLException se){
        se.printStackTrace();
    }finally {
      checkDanglingTransaction();
    }return "Login failed\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    boolean deadlock = false;
    int retrycount = 0;
    do{
    try {
      // TODO: YOUR CODE HERE
      if(initAmount < 0){
        return "Failed to create user\n";
      }
      conn.setAutoCommit(false);
      checkUsernameStatement.clearParameters();
      checkUsernameStatement.setString(1, username);      // Sets the first parameter (the first “?”) to the value of the variable “originCity”
      ResultSet rs = checkUsernameStatement.executeQuery();
      if(!rs.next()){
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        // Specify the hash parameters
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);

        // Generate the hash
        SecretKeyFactory factory = null;
        byte[] hash = null;
        try {
          factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
          hash = factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
          throw new IllegalStateException();
        }

        insertUserStatement.clearParameters();
        insertUserStatement.setString(1, username);      // Sets the first parameter (the first “?”) to the value of the variable “originCity”
        insertUserStatement.setBytes(2, hash);
        insertUserStatement.setBytes(3, salt);
        insertUserStatement.setInt(4, initAmount);
        insertUserStatement.executeUpdate();
        conn.commit();
        conn.setAutoCommit(true);
        return "Created user " + username +"\n";

      }else{
        conn.commit();
        conn.setAutoCommit(true);
        return "Failed to create user\n";
      }
    } catch(SQLException se){
      try{
        conn.rollback();
        conn.setAutoCommit(true);
        deadlock = isDeadLock(se);
        if(!deadlock){
          deadlock = false;
          se.printStackTrace();
          return "Failed to create user\n";
        }
        retrycount = retrycount + 1;
//         System.out.println("Deadlock retry: " + (retrycount));
      }
      catch (SQLException se2){
        se2.printStackTrace();
      }
    } finally {
      checkDanglingTransaction();
    }
  }while(deadlock && retrycount<10);
    System.out.println("Deadlock retry timeout");
    return "Failed to create user\n";
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
   * is searches for direct flights and flights with two "hops." Only searches for up to the number
   * of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight,
      int dayOfMonth, int numberOfItineraries) {
    try {
      // WARNING the below code is unsafe and only handles searches for direct flights
      // You can use the below code as a starting reference point or you can get rid
      // of it all and replace it with your own implementation.
      //
      // TODO: YOUR CODE HERE

      StringBuffer sb = new StringBuffer();
      output.clear();
      // conn.setAutoCommit(false);
      try {
        // one hop itineraries
        String unsafeSearchSQL = "SELECT TOP (" + numberOfItineraries
            + ") fid, day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price "
            + "FROM Flights " + "WHERE origin_city = \'" + originCity + "\' AND dest_city = \'"
            + destinationCity + "\' AND canceled = 0 AND day_of_month =  " + dayOfMonth + " "
            + "ORDER BY actual_time ASC";

        Statement searchStatement = conn.createStatement();
        ResultSet oneHopResults = searchStatement.executeQuery(unsafeSearchSQL);

        int rs_count = 0; // keep track of the number of output
        if (!oneHopResults.next() && directFlight) {
          oneHopResults.close();
          return "No flights match your selection\n";
        }
        do {
          int fid = oneHopResults.getInt("fid");
          int result_dayOfMonth = oneHopResults.getInt("day_of_month");
          String result_carrierId = oneHopResults.getString("carrier_id");
          String result_flightNum = oneHopResults.getString("flight_num");
          String result_originCity = oneHopResults.getString("origin_city");
          String result_destCity = oneHopResults.getString("dest_city");
          int result_time = oneHopResults.getInt("actual_time");
          int result_capacity = oneHopResults.getInt("capacity");
          int result_price = oneHopResults.getInt("price");
          
          // initialize the Flight obj
          Flight f = new Flight();
          f.fid = fid;
          f.dayOfMonth = result_dayOfMonth;
          f.carrierId = result_carrierId;
          f.flightNum = result_flightNum;
          f.originCity = result_originCity;
          f.destCity = result_destCity;
          f.time = result_time;
          f.capacity = result_capacity;
          f.price = result_price;

          Itinerary direct_Itin = new Itinerary(rs_count, true, f, null);
          output.add(direct_Itin);
          // sb.append(direct_Itin.toSring());

          rs_count++; // update the count

          // sb.append("Day: " + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: "
          //     + result_flightNum + " Origin: " + result_originCity + " Destination: "
          //     + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity
          //     + " Price: " + result_price + "\n");
        } while (oneHopResults.next()) ;
        oneHopResults.close();
        // only check for indirect flights when there are not enough direct flights and the direct flight
        // flag is set to 0
        if (rs_count < numberOfItineraries && !directFlight) {
          int new_num_Itin = numberOfItineraries - rs_count;
          String new_SearchSQL = "SELECT TOP (" + new_num_Itin
            + ") f1.fid as f1_fid, f1.day_of_month as f1_day_of_month, f1.carrier_id as f1_carrier_id, f1.flight_num as f1_flight_num, f1.origin_city as f1_origin_city, f1.dest_city as f1_dest_city, f1.actual_time as f1_actual_time, f1.capacity as f1_capacity, f1.price as f1_price, "
            + "f2.fid as f2_fid, f2.day_of_month as f2_day_of_month, f2.carrier_id as f2_carrier_id, f2.flight_num as f2_flight_num, f2.origin_city as f2_origin_city, f2.dest_city as f2_dest_city, f2.actual_time as f2_actual_time, f2.capacity as f2_capacity, f2.price as f2_price "
            + "FROM Flights as f1, Flights as f2 " + "WHERE f1.origin_city = \'" + originCity + "\' AND f2.dest_city = \'"
            + destinationCity + "\' AND f1.dest_city = f2.origin_city AND f1.day_of_month = f2.day_of_month AND f1.day_of_month =  " + dayOfMonth + " "
            + "AND f1.canceled = 0 and f2.canceled = 0 ORDER BY f1.actual_time+f2.actual_time ASC";

          ResultSet new_results = searchStatement.executeQuery(new_SearchSQL);

          while (new_results.next()) {
            Flight f1 = new Flight();
            f1.fid = new_results.getInt("f1_fid");
            f1.dayOfMonth = new_results.getInt("f1_day_of_month");
            f1.carrierId = new_results.getString("f1_carrier_id");
            f1.flightNum = new_results.getString("f1_flight_num");
            f1.originCity = new_results.getString("f1_origin_city");
            f1.destCity = new_results.getString("f1_dest_city");
            f1.time = new_results.getInt("f1_actual_time");
            f1.capacity = new_results.getInt("f1_capacity");
            f1.price = new_results.getInt("f1_price");

            Flight f2 = new Flight();
            f2.fid = new_results.getInt("f2_fid");
            f2.dayOfMonth = new_results.getInt("f2_day_of_month");
            f2.carrierId = new_results.getString("f2_carrier_id");
            f2.flightNum = new_results.getString("f2_flight_num");
            f2.originCity = new_results.getString("f2_origin_city");
            f2.destCity = new_results.getString("f2_dest_city");
            f2.time = new_results.getInt("f2_actual_time");
            f2.capacity = new_results.getInt("f2_capacity");
            f2.price = new_results.getInt("f2_price");

            Itinerary indirect_Itin = new Itinerary(rs_count, directFlight, f1, f2);
            output.add(indirect_Itin);
            // sb.append(undirect_Itin.toSring());

            rs_count++;
          }
          if (rs_count == 0) {
            new_results.close();
            return "No flights match your selection\n";
          }
          new_results.close();
        }
        Collections.sort(output);
        for (int i = 0; i < output.size(); i++) {
          String cur_output = "Itinerary " + i + output.get(i).toSring();
          sb.append(cur_output);
        }
        return sb.toString();
      } catch (SQLException e) {
        e.printStackTrace();
        // conn.setAutoCommit(true);
        return "Failed to search\n";
      }
    }  finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *                    the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   *         If the user is trying to book an itinerary with an invalid ID or without having done a
   *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
   *         a reservation on the same day as the one that they are trying to book now, then return
   *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
   *         failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from 1 and
   *         increments by 1 each time a successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    boolean deadlock = false;
    int retryCount = 0;
    do{
    try {
      // TODO: YOUR CODE HERE
      if(!login){
        return "Cannot book reservations, not logged in\n";
      }else if(output == null || itineraryId > output.size() - 1){
        return "No such itinerary " + itineraryId + "\n";
      }
      Itinerary itinerary = output.get(itineraryId);
      Flight flight1 = itinerary.flight1;
      Flight flight2 = itinerary.flight2;
      int date = flight1.dayOfMonth;
      checkSameDay.clearParameters();
      checkSameDay.setString(1, user);
      checkSameDay.setInt(2, date);
      conn.setAutoCommit(false);
      ResultSet rs = checkSameDay.executeQuery();
      if(rs.next()){
        conn.rollback();
        conn.setAutoCommit(true);
        return "You cannot book two flights in the same day\n";
      }
      rs.close();
      int f1Seats = remainCapacity(flight1.fid);
      int f2Seats = 0;
      if(flight2 != null){
        f2Seats = remainCapacity(flight2.fid);
      }
      if((f1Seats > 0) && (flight2 == null || f2Seats>0)){
        int fid1 = flight1.fid;
        int fid2 = (flight2 == null) ? -1:flight2.fid;
        int price1 = flight1.price;
        int price2 = (flight2 == null) ? 0:flight2.price;
        insertReservation.clearParameters();
        insertReservation.setString(1, user);      // Sets the first parameter (the first “?”) to the value of the variable “originCity”
        insertReservation.setInt(2, fid1);
        insertReservation.setInt(3, fid2);
        insertReservation.setBoolean(4, false);
        insertReservation.setBoolean(5, false);
        insertReservation.setInt(6, price1 + price2);
        insertReservation.executeUpdate();

        updateCapacity.clearParameters();
        updateCapacity.setInt(1,f1Seats-1);
        updateCapacity.setInt(2,fid1);
        updateCapacity.executeUpdate();
        if(flight2 != null){
          updateCapacity.clearParameters();
          updateCapacity.setInt(1,f2Seats-1);
          updateCapacity.setInt(2,fid2);
          updateCapacity.executeUpdate();
        }

        getRid.clearParameters();
        ResultSet rs1 = getRid.executeQuery();
        rs1.next();
        int rid = rs1.getInt("rid");
        conn.commit();
        conn.setAutoCommit(true);
        return "Booked flight(s), reservation ID: "+ rid + "\n";
      }else{
        conn.commit();
        conn.setAutoCommit(true);
        return "Booking failed\n";
      }
    } catch (SQLException e){
      try{
        conn.rollback();
        conn.setAutoCommit(true);
        deadlock = isDeadLock(e);
        if(!deadlock){
          e.printStackTrace();
          deadlock = false;
          return "Booking failed\n";
        }
        retryCount= retryCount + 1;
//        System.out.println("Deadlock retry: "+ (retryCount));
      }catch (SQLException a){
        a.printStackTrace();
        return "Booking failed\n";
      }
    }
      finally {
      checkDanglingTransaction();
    }
    }while(deadlock);
    System.out.println("Deadlock retry timeout");
    return "Failed to create user\n";
  }


  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   *         is not found / not under the logged in user's name, then return "Cannot find unpaid
   *         reservation [reservationId] under user: [username]\n" If the user does not have enough
   *         money in their account, then return "User has only [balance] in account but itinerary
   *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
   *         [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    try {
      int price;
      int fid1;
      int fid2;

      if(!login){
        return "Cannot pay, not logged in\n";
      }

      getReservation.clearParameters();
      getReservation.setInt(1, reservationId);
      getReservation.setString(2, user);
      getReservation.setBoolean(3, false);
//      conn.execute("BEGIN TRANSACTION;");
      conn.setAutoCommit(false);
      ResultSet rs = getReservation.executeQuery();
      if(!rs.next()){
        conn.commit();
        conn.setAutoCommit(true);
        return "Cannot find unpaid reservation " + reservationId + " under user: " + user +"\n";
      }else{
         fid1 = rs.getInt("fid1");
         fid2 = rs.getInt("fid2");
         price = rs.getInt("price");
      }
      checkUsernameStatement.clearParameters();
      checkUsernameStatement.setString(1, user);
      ResultSet rs2 = checkUsernameStatement.executeQuery();
      rs2.next();
      int balance = rs2.getInt("balance");
      if(balance < price){
        conn.commit();
        conn.setAutoCommit(true);
        return "User has only " + balance + " in account but itinerary costs " + price + "\n";
      }
      int newBalance = balance - price;
      updateBalance.clearParameters();
      updateBalance.setInt(1, newBalance);
      updateBalance.setString(2, user);
      updateBalance.executeUpdate();

      updateReservation.clearParameters();
      updateReservation.setInt(1, reservationId);
      updateReservation.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      return "Paid reservation: " + reservationId + " remaining balance: " + newBalance + "\n";
    }
    catch(SQLException a){
      try{
        conn.rollback();
        conn.setAutoCommit(true);
        a.printStackTrace();
        return "Failed to pay for reservation " + reservationId + "\n";
      }catch(SQLException a2){
        a2.printStackTrace();
        return "Failed to pay for reservation " + reservationId + "\n";
      }
    }
    finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    try {
      // TODO: YOUR CODE HERE
      if (!login) {
        return "Cannot view reservations, not logged in\n";
      }

      conn.setAutoCommit(false);

      listValidReservation.clearParameters();
      listValidReservation.setString(1, user);
      ResultSet rs = listValidReservation.executeQuery();
      if (!rs.next()) {
        rs.close();
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      }
      StringBuffer sb = new StringBuffer();
      do { 
        int rid = rs.getInt("rid");
        int fid1 = rs.getInt("fid1");
        int fid2 = rs.getInt("fid2");
        boolean paid = rs.getBoolean("paid");
        getFlightInfo.clearParameters();
        getFlightInfo.setInt(1, fid1);
        ResultSet rs_1 = getFlightInfo.executeQuery();
        Flight f1 = new Flight();
        rs_1.next();
        f1.fid = rs_1.getInt("fid");
        f1.dayOfMonth = rs_1.getInt("day_of_month");
        f1.carrierId = rs_1.getString("carrier_id");
        f1.flightNum = rs_1.getString("flight_num");
        f1.originCity = rs_1.getString("origin_city");
        f1.destCity = rs_1.getString("dest_city");
        f1.time = rs_1.getInt("actual_time");
        f1.capacity = rs_1.getInt("capacity");
        f1.price = rs_1.getInt("price");
        rs_1.close();
        Flight f2 = null;
        if (fid2 != -1) {
          f2 = new Flight();
          getFlightInfo.clearParameters();
          getFlightInfo.setInt(1, fid2);
          ResultSet rs_2 = getFlightInfo.executeQuery();
          rs_2.next();
          f1.fid = rs_2.getInt("fid");
          f2.dayOfMonth = rs_2.getInt("day_of_month");
          f2.carrierId = rs_2.getString("carrier_id");
          f2.flightNum = rs_2.getString("flight_num");
          f2.originCity = rs_2.getString("origin_city");
          f2.destCity = rs_2.getString("dest_city");
          f2.time = rs_2.getInt("actual_time");
          f2.capacity = rs_2.getInt("capacity");
          f2.price = rs_2.getInt("price");
          rs_2.close();
        }
        if (paid) {
          sb.append("Reservation " + rid + " paid: true:\n");
        } else {
          sb.append("Reservation " + rid + " paid: false:\n");
        }
        sb.append(f1.toString() + "\n");
        if (fid2 != -1) {
          sb.append(f2.toString() + "\n");
        }
      } while(rs.next());
      rs.close();
      conn.commit();
      conn.setAutoCommit(true);
      return sb.toString();
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to retrieve reservations\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *         all other errors, return "Failed to cancel reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    try {
      // TODO: YOUR CODE HERE
      if (!login) {
        return "Cannot cancel reservations, not logged in\n";
      }
      conn.setAutoCommit(false);
      listSingleValidReservation.clearParameters();
      listSingleValidReservation.setString(1, user);
      listSingleValidReservation.setInt(2, reservationId);
      ResultSet rs = listSingleValidReservation.executeQuery();
      if (!rs.next()) {
        rs.close();
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to cancel reservation " + reservationId + "\n";
      }
      int fid1 = rs.getInt("fid1");
      int fid2 = rs.getInt("fid2");
      int price = rs.getInt("price");
      boolean paid = rs.getBoolean("paid");
      checkUsernameStatement.clearParameters();
      checkUsernameStatement.setString(1, user);
      ResultSet rs_1 = checkUsernameStatement.executeQuery();
      rs_1.next();
      int cur_balance = rs_1.getInt("balance");
      if (paid) {
        int new_balance = cur_balance + price;
        updateBalance.clearParameters();
        updateBalance.setInt(1, new_balance);
        updateBalance.setString(2, user);
        updateBalance.executeUpdate();
      }
      // update flight1
      int cur_cap_f1 = remainCapacity(fid1);
      updateCapacity.clearParameters();
      updateCapacity.setInt(1, cur_cap_f1 + 1);
      updateCapacity.setInt(2, fid1);
      updateCapacity.executeUpdate();
      // update flight2 if exists
      if (fid2 != -1) {
        int cur_cap_f2 = remainCapacity(fid2);
        updateCapacity.clearParameters();
        updateCapacity.setInt(1, cur_cap_f2 + 1);
        updateCapacity.setInt(2, fid2);
        updateCapacity.executeUpdate();
      }
      //delete current reservation
      deleteReservation.clearParameters();
      deleteReservation.setInt(1, reservationId);
      deleteReservation.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      rs.close();
      rs_1.close();
      return "Canceled reservation " + reservationId +"\n";
    } catch (SQLException e){
      try{
        conn.rollback();
        conn.setAutoCommit(true);
        if(!isDeadLock(e)){
          e.printStackTrace();
          return "Failed to cancel reservation" + reservationId + "\n";
        }
        // System.out.println("Deadlock retry: " + (retrycount+1));
      }
      catch (SQLException e2){
        e2.printStackTrace();
      }
      return "Failed to cancel reservation" + reservationId + "\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  private int remainCapacity(int fid) throws SQLException{
      int capacity = 0;
      getCapacity.clearParameters();
      getCapacity.setInt(1, fid);
      ResultSet rs = getCapacity.executeQuery();
      if(rs.next()){
        capacity = rs.getInt("freeSeat");
      }else{
        capacity = checkFlightCapacity(fid);
        insertCapacity.clearParameters();
        insertCapacity.setInt(1, fid);      // Sets the first parameter (the first “?”) to the value of the variable “originCity”
        insertCapacity.setInt(2, capacity);
        insertCapacity.executeUpdate();
      }
      return capacity;
  }

  /**
   * Throw IllegalStateException if transaction not completely complete, rollback.
   * 
   */
  private void checkDanglingTransaction() {
    try {
      try (ResultSet rs = tranCountStatement.executeQuery()) {
        rs.next();
        int count = rs.getInt("tran_count");
        if (count > 0) {
          throw new IllegalStateException(
              "Transaction not fully commit/rollback. Number of transaction in process: " + count);
        }
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database error", e);
    }
  }

  private static boolean isDeadLock(SQLException ex) {
    return ex.getErrorCode() == 1205;
  }

  /**
   * A class to store flight information.
   */

  class Itinerary implements Comparable<Itinerary>{
    public int id;
    public int flightCount;
    public boolean directFlight;
    public int totalFlightTime;
    Flight flight1;
    Flight flight2;

    public Itinerary(int id, boolean directFlight, Flight flight1, Flight flight2){
      this.id = id;
      this.directFlight =directFlight;
      this.flight1 = flight1;
      this.flight2 = flight2;
      if(directFlight){
        flightCount = 1;
        totalFlightTime = flight1.time;
      }else{
        flightCount =2;
        totalFlightTime = flight1.time + flight2.time;
      }
    }

    public int compareTo(Itinerary a){
      if(this.totalFlightTime < a.totalFlightTime){
        return -1;
      }else if(this.totalFlightTime == a.totalFlightTime){
        if (this.flight1.fid != a.flight1.fid){
          return this.flight1.fid - a.flight1.fid;
        }else{
          return this.flight2.fid - a.flight2.fid;
        } 
      }else{
        return 1;
      }
    }

    public String toSring(){
      String str1 = ": " + flightCount + " flight(s), " + totalFlightTime +" minutes\n";
      if (directFlight){
        return str1 + flight1.toString() + "\n";
      }else{
        return str1 + flight1.toString() + "\n" + flight2.toString() + "\n";
      }
    }
  }


  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
