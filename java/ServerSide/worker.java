package ServerSide;

import Organizations.Organization;
//import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.*;
import java.util.Set;


public class Worker implements Runnable{

    private Socket socket;
//    private Logger logger;
    Set<Organization> organizations;
    Connection connection;


    public Worker(Socket socket, Connection connection, Set<Organization> organizations) throws SQLException {
        this.socket = socket;
//        this.logger = logger;
        this.organizations = organizations;
        this.connection = connection;
        connection.setAutoCommit(false);
    }

    @Override
    public void run() {
//        logger.info("Connection from " + socket + "!");
        try (ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream())) {
            objectOutputStream.writeObject("Connection establish.");
            long userid=-1;
            while(true)
            {
                String login;
                String password;
                switch ((String) objectInputStream.readObject()){
                    case "login":
                        login = (String) objectInputStream.readObject();
                        password = (String) objectInputStream.readObject();
                        userid = login(login, password);
                        break;
                    case "reg":
                        login = (String) objectInputStream.readObject();
                        password = (String) objectInputStream.readObject();
                        userid = reg(login, password);
                        break;
                }
                if (userid==-1)
                    objectOutputStream.writeObject("Пользователь не найден");
                else if (userid == -2)
                    objectOutputStream.writeObject("Такое имя пользователя уже занято");
                else {
                    objectOutputStream.writeObject("Ok");
                    break;
                }
            }

            //System.out.println("USERID1:" + userid);
            ServerCommandReader reader = new ServerCommandReader(objectOutputStream, objectInputStream, connection, userid);

//            logger.info("Start messages exchange");

            reader.start_listening(organizations);

        } catch (IOException | ClassNotFoundException | SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public int login(String login, String password_hashed) throws SQLException {
        PreparedStatement strm = this.connection.prepareStatement("SELECT * FROM SAVVA_USERS WHERE login=? AND password =?");
        strm.setString(1, login);
        strm.setString(2, password_hashed);
        ResultSet rs = strm.executeQuery();
        try
        {
            rs.next();
            return rs.getInt("id");
        } catch (Exception e){
            return -1;
        }
    }

    public int reg(String login, String password_hashed) throws SQLException {
            try{
                PreparedStatement strm = this.connection.prepareStatement("SELECT * FROM SAVVA_USERS WHERE login=?");
                strm.setString(1, login);
                ResultSet rs = strm.executeQuery();
                connection.commit();
                while (rs.next())
                {
                    return -2;
                }
        } catch (SQLException e) {
                e.printStackTrace();
            }

            PreparedStatement strm  = this.connection.prepareStatement("INSERT INTO SAVVA_USERS (login, password, id) VALUES (?, ?, nextval('users_hramova_sequence'))");
            strm.setString(1, login);
            strm.setString(2, password_hashed);
            strm.execute();
            connection.commit();
            return login(login, password_hashed);
        }
}
