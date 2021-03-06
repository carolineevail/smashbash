package com.teamSmash;

import java.sql.*;
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import jodd.json.JsonSerializer;
import spark.Session;
import spark.Spark;
import java.util.*;

public class Main {

    public static void createTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS account (account_id IDENTITY, account_name VARCHAR, account_password VARCHAR)");
        stmt.execute("CREATE TABLE IF NOT EXISTS event (event_id IDENTITY, event_name VARCHAR, event_location VARCHAR, event_time VARCHAR, " +
                "event_date DATE, event_image VARCHAR, event_description VARCHAR, event_owner INT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS account_event_map (map_id IDENTITY, account_id INT, event_id INT)");
        stmt.close();
    }

    public static void deleteTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.equals("DROP TABLE account");
        stmt.equals("DROP TABLE event");
        stmt.equals("DROP TABLE account_event_map");
    }

    public static int createAccount(Connection conn, String name, String password) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO account VALUES (NULL, ?, ?)");
        stmt.setString(1, name);
        stmt.setString(2, password);

        return stmt.executeUpdate();
    }

    public static int createEvent(Connection conn, String name, String location, String time,
                                  String date, String image, String description, int accountId) throws SQLException, ParseException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO event VALUES (NULL, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
        //prepare a time formatter
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date parsedDate = new java.sql.Date(formatter.parse(date).getTime());


        stmt.setString(1, name);
        stmt.setString(2, location);
        stmt.setString(3, time);  //here I am needing to convert a LocalTime object into a Time object with the DB will accept more freely. I think.
        stmt.setDate(4, (parsedDate));  //same here but for Date.
        stmt.setString(5, image);
        stmt.setString(6, description);
        stmt.setInt(7, accountId);
        int affected = stmt.executeUpdate(); //this is so I can check to see if something was created

        int eventId = 0;  //just have to initialize this I guess

        ResultSet result = stmt.getGeneratedKeys();  //this should contain the auto-generated ID

        if (result.next()) {
            eventId = result.getInt(1);  //assign the eventId to the auto-generated ID
        }
        stmt.close();

        mapUserToEvent(conn, accountId, eventId);

        return affected;
    }

    //this is the table we will use to populate lists of all events being attending by a user
    //as well as all users going to an event
    //this is being run every time a user enters a new event.
    public static void mapUserToEvent(Connection conn, int accountId, int eventId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO account_event_map VALUES(NULL, ?, ?)");
        stmt.setInt(1, accountId);
        stmt.setInt(2, eventId);
        stmt.execute();
        stmt.close();
    }

    //return an ArrayList of all accounts in the DB. Probably won't need this
    public static ArrayList<Account> selectAccounts(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM account");
        ResultSet results = stmt.executeQuery();

        ArrayList<Account> accountList = new ArrayList<>();

        while (results.next()) {
            int id = results.getInt(1);
            String name = results.getString(2);
            String password = results.getString(3);
            Account a = new Account(id, name, password);
            accountList.add(a);
        }

        stmt.close();
        return accountList;
    }

    //get an account by the account name
    public static Account selectAccount(Connection conn, String name) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM account WHERE account_name = ?");
        stmt.setString(1, name);

        ResultSet result = stmt.executeQuery();

        if (result.next()) {
            Account account = new Account(result.getInt(1), result.getString(2), result.getString(3));
            return account;
        } else {
            Account account = null;
            return account;
        }
    }

    //get an account by the account id
    public static Account selectAccount(Connection conn, int id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM account WHERE account_id = ?");
        stmt.setInt(1, id);

        ResultSet result = stmt.executeQuery();

        if (result.next()) {
            Account account = new Account(result.getInt(1), result.getString(2), result.getString(3));
            return account;
        } else {
            Account account = null;
            return account;
        }
    }


    //I hope that this method is limiting output to dates from the current date to three months from the current date.
    //Returns an ArrayList of all the events in the DB
    public static ArrayList<Event> selectEvents(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM event WHERE event_date BETWEEN CURRENT_DATE AND DATEADD(MONTH, 3, CURRENT_DATE )");
        ResultSet results = stmt.executeQuery();

        ArrayList<Event> eventList = new ArrayList<>();

        while (results.next()) {
            eventList.add(buildEventFromDb(results));
        }
        return eventList;
    }

    //select a single event by event id
    public static Event selectEvent(Connection conn, int id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM event WHERE event_id = ?");
        stmt.setInt(1, id);
        ResultSet results = stmt.executeQuery();

        if (results.next()) {
            Event event = buildEventFromDb(results);
            return event;
        } else {
            return null;
        }
    }

    //this will return an ArrayList of events that were created by a specific user.
    public static ArrayList<Event> selectEventsCreatedByAccount(Connection conn, int accountId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM event WHERE event_owner = ?");
        stmt.setInt(1, accountId);
        ResultSet results = stmt.executeQuery();

        ArrayList<Event> eventsByAccountList = new ArrayList<>();

        while (results.next()) {
            Event event = buildEventFromDb(results);
            eventsByAccountList.add(event);
        }

        stmt.close();
        return eventsByAccountList;
    }

    public static void deleteEvent (Connection conn, int eventId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("DELETE FROM event WHERE event_id = ?");
        stmt.setInt(1, eventId);
        stmt.execute();
        PreparedStatement stmtTwo = conn.prepareStatement("DELETE FROM account_event_map WHERE event_id = ?");
        stmtTwo.setInt(1, eventId);
        stmtTwo.execute();
    }

    public static void editEvent(Connection conn, int eventId, String name, String location, String time, String date, String image, String description, int accountId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("UPDATE event SET event_name = ?, event_location = ?, " +
                "event_time = ?, event_date = ?, event_image = ?, event_description = ?, event_owner = ?" +
                "WHERE event_id = ?");
        stmt.setString(1, name);
        stmt.setString(2, location);
        stmt.setString(3, time);
        stmt.setDate(4, Date.valueOf(date));
        stmt.setString(5, image);
        stmt.setString(6, description);
        stmt.setInt(7, accountId);
        stmt.setInt(8, eventId);
        stmt.execute();
    }

    //once i know this works i can take some of the SQL out. I'm currently getting more fields than i actually need.
    //this method will take a account ID number and it will return an arraylist of all of the events they are attending
    // (in a special class made for that purpose)
    public static ArrayList<AccountEvents> getAccountEvents(Connection conn, int accountId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT m.event_id, e.event_name " +
                                                        "FROM account_event_map m " +
                                                        "INNER JOIN event e ON m.event_id = e.event_id " +
                                                        "WHERE m.account_id = ? ");
        stmt.setInt(1, accountId);
        ResultSet results = stmt.executeQuery();
        ArrayList<AccountEvents> accountEventsList = new ArrayList<>();


//        ResultSetMetaData rsmd = results.getMetaData();
//
//        String name = rsmd.getColumnName(1);
//        String name2 = rsmd.getColumnName(2);
//        System.out.printf("STTTOPPPP");

        while (results.next()) {
            int eventId = results.getInt(1);
            String eventName = results.getString(2);

            Account account = selectAccount(conn, accountId);
            Event event = selectEvent(conn, eventId);
            accountEventsList.add(new AccountEvents(account, event));
        }
        return accountEventsList;
    }


    public static int getAccountId(Connection conn, String name) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT account_id FROM account WHERE account_name = ?");
        stmt.setString(1, name);
        ResultSet results = stmt.executeQuery();

        int accountId = 0;
        while (results.next()) {
            accountId = results.getInt(1);
        }

        return accountId;
    }

    //just broke the logic up here a little bit.
    //This just takes runs in a resultSet while loop and taks the data from the RS and builds it into an Event object and returns that.
    public static Event buildEventFromDb(ResultSet results) throws SQLException {
        int id = results.getInt(1);
        String name = results.getString(2);
        String location = results.getString(3);
        String time = results.getString(4);
        Date date = results.getDate(5);
        String image = results.getString(6);
        String description = results.getString(7);
        int accountId = results.getInt(8);
        Event event = new Event(id, name, location, time, date.toLocalDate(), image, description, accountId);
      //  public Event(int id, String name, String location, String time, LocalDate date, String image, String description, int eventOwner) {
        return event;
    }

    public static void main(String[] args) throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:h2:./main");
        deleteTables(conn);
        createTables(conn);

        Spark.externalStaticFileLocation("public");

        Spark.init();

        //remember to remove this once everything is functioning.
        Spark.get(
                "/login",
                ((request, response) -> {
                    JsonSerializer s = new JsonSerializer();
                    return s.serialize(selectAccounts(conn));
                })
        );
        Spark.get(
                "/events",
                ((request, response) -> {
                    JsonSerializer s = new JsonSerializer();
                    return s.serialize(selectEvents(conn));
                })
        );

        Spark.get(
                "/event",
                ((request, response) -> {
                    int eventId = Integer.valueOf(request.queryParams("id")) ;

                    JsonSerializer s = new JsonSerializer();
                    return s.serialize(selectEvent(conn, eventId));
                })
        );
        Spark.get(
                "/accountEvents",
                ((request1, response1) -> {
                    Session session = request1.session();
                    String name = session.attribute("userName");

                    int accountId = getAccountId(conn, name);


                    JsonSerializer s = new JsonSerializer();
                    return s.serialize(selectEventsCreatedByAccount(conn, accountId));
                })
        );
        Spark.post(
                "/login",
                ((request, response) -> {
                    //get user input
                    String name = request.queryParams("username");
                    String password = request.queryParams("password");
                    //grab account from DB by name if it exists (null if not)
                    Account account = selectAccount(conn, name);

                    JsonSerializer serializer = new JsonSerializer();



                    //create a session
                    Session session = request.session();

                    if ( (account != null) && (password.equals(account.getPassword())) ) {  //if exist and the pass matches
                        int id = getAccountId(conn, name);
                        session.attribute("userName", name);
                        return serializer.serialize(selectAccount(conn, id));
                    } else if (account == null) {   //if the user does not yet exist, create it
                        createAccount(conn, name, password);
                        int id = getAccountId(conn, name);
                        session.attribute("userName", name);
                        return serializer.serialize(selectAccount(conn, id));
                    } else {
                        return "Password mismatch";
                    }
                })
        );
        Spark.post(
                "/createEvent",
                ((request, response) -> {
                    Session session = request.session();

                    String userName = session.attribute("userName");
                    int userId = getAccountId(conn, userName);

                    String name = request.queryParams("eventName");
                    String location = request.queryParams("eventLocation");
                    String date = null;
                    String time = null;
                    if(!request.queryParams("time").equals("")){
                        time = request.queryParams("time");
                    }

                    if(!request.queryParams("date").equals("")){
                        date = request.queryParams("date");
                    }

                    String image = request.queryParams("image");
                    String description = request.queryParams("description");
                    createEvent(conn, name, location, time, date, image, description, userId);
                    return "";
                })
        );
        Spark.post(
                "/deleteEvent",
                ((request, response) -> {
                    int eventId = Integer.valueOf(request.queryParams("eventId"));
                    deleteEvent(conn, eventId);
                    return "";
                })
        );
        Spark.post(
                "/editEvent",
                ((request, response) -> {

                    int eventId = Integer.valueOf(request.queryParams("eventId"));
                    String name = request.queryParams("eventName");
                    String location = request.queryParams("eventLocation");
                    String date = null;
                    String time = null;
                    if(!request.queryParams("time").equals("")){
                        time = request.queryParams("time");
                    }
                    if(!request.queryParams("date").equals("")){
                        date = request.queryParams("date");
                    }
                    String image = request.queryParams("image");
                    String description = request.queryParams("description");
                    int accountId = Integer.valueOf(request.queryParams("accountId"));
                    editEvent(conn, eventId, name, location, time, date, image, description, accountId);
                    return "";
                })
        );
        Spark.post(
                "/logout",
                ((request, response) -> {
                    Session session = request.session();
                    session.invalidate();
                    return "";
                })
        );
    }
}
