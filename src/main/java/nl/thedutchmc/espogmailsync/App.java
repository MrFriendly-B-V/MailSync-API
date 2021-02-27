package nl.thedutchmc.espogmailsync;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

import nl.thedutchmc.espogmailsync.database.SqlManager;
import nl.thedutchmc.espogmailsync.runnables.EspoAccountSyncRunnableV2;
import nl.thedutchmc.espogmailsync.runnables.EspoContactSyncRunnableV2;
import nl.thedutchmc.espogmailsync.runnables.FetchMailThreadV2;

@SpringBootApplication
@PropertySource("classpath:application.properties")
public class App {

	public static volatile boolean DEBUG = false;
	public static volatile boolean RUNNING = true;
	
	private static SqlManager sqlManager;
	private static Environment environment;	
	
    public static void main(String[] args) {
    	
    	App.environment = new Environment();
    	
    	App.logInfo("Running preflight checks...");
    	
    	//Check debug mode
    	String debugModeStr = System.getenv("DEBUG");
    	if(debugModeStr != null && debugModeStr.equalsIgnoreCase("true")) {
    		App.logInfo("Environmental variable 'DEBUG' set to true. Running with DEBUG level logging!");
    		App.DEBUG = true;
    	}
    	
    	//Read and check environmental variables
    	String authApiToken = System.getenv("AUTH_API_TOKEN");
    	if(authApiToken != null) {
    		App.environment.setAuthApiToken(authApiToken);
    	} else {
    		App.logError("Environmental variable 'AUTH_API_TOKEN' not defined!");
    		System.exit(1);
    	}
    	
    	String authServerHost = System.getenv("AUTH_SERVER_HOST");
    	if(authServerHost != null) {
    		App.environment.setAuthServerHost(authServerHost);
    	} else {
    		App.logError("Environmental variable 'AUTH_SERVER_HOST' not defined!");
    		System.exit(1);
    	}
    	
    	String frontendHost = System.getenv("FRONTEND_HOST");
    	if(frontendHost != null) {
    		App.environment.setFrontendHost(frontendHost);
    	} else {
    		App.logError("Environmental variable 'FRONTEND_HOST' not defined!");
    		System.exit(1);
    	}
    	
    	String espoHost = System.getenv("ESPO_HOST");
    	if(espoHost != null) {
    		App.environment.setEspoHost(espoHost);
    	} else {
    		App.logError("Environmental variable 'ESPO_HOST' not defined!");
    		System.exit(1);
    	}
    	
    	String espoApiKey = System.getenv("ESPO_API_KEY");
    	if(espoApiKey != null) {
    		App.environment.setEspoApiKey(espoApiKey);
    	} else {
    		App.logError("Environmental variable 'ESPO_API_KEY' not defined!");
    		System.exit(1);
    	}
    	
    	String espoSecretKey = System.getenv("ESPO_SECRET_KEY");
    	if(espoSecretKey != null) {
    		App.environment.setEspoSecretKey(espoSecretKey);
    	} else {
    		App.logError("Environmental variable 'ESPO_SECRET_KEY' not defined!");
    		System.exit(1);
    	}
    	
    	String mysqlHost = System.getenv("MYSQL_HOST");
    	if(mysqlHost != null) {
    		App.environment.setMysqlHost(mysqlHost);
    	} else {
    		App.logError("Environmental variable 'MYSQL_HOST' not defined!");
    		System.exit(1);
    	}
    	
    	String mysqlDb = System.getenv("MYSQL_DB");
    	if(mysqlDb != null) {
    		App.environment.setMysqlDb(mysqlDb);
    	} else {
    		App.logError("Environmental variable 'MYSQL_DB' not defined!");
    		System.exit(1);
    	}
    	
    	String mysqlUsername = System.getenv("MYSQL_USERNAME");
    	if(mysqlUsername != null) {
    		App.environment.setMysqlUsername(mysqlUsername);
    	} else {
    		App.logError("Environmental variable 'MYSQL_USERNAME' not defined!");
    		System.exit(1);
    	}
    	
    	String mysqlPassword = System.getenv("MYSQL_PASSWORD");
    	if(mysqlPassword != null) {
    		App.environment.setMysqlPassword(mysqlPassword);
    	} else {
    		App.logError("Environmental variable 'MYSQL_PASSWORD' not defined!");
    		System.exit(1);
    	}
    	
    	App.sqlManager = new SqlManager();
    	
    	App.logInfo("Fetching users from database.");
    	List<String> activeUsers = new ArrayList<>();
    	try {
    		final String query = "SELECT id FROM users";
    		PreparedStatement pr = sqlManager.createPreparedStatement(query);
    		ResultSet rs = sqlManager.executeFetchStatement(pr);
    		
    		while(rs.next()) {
    			activeUsers.add(rs.getString("id"));
    		}
    	} catch(SQLException e) {
    		e.printStackTrace();
    	}
    	
    	//Start fetch mail thread for each user
    	App.logInfo(String.format("Starting FetchMailThread for %d users.", activeUsers.size()));
    	activeUsers.forEach(user -> {    		
    		Thread fetchMailThreadV2 = new Thread(new FetchMailThreadV2(user), "FetchMailThreadV2-" + user);
    		fetchMailThreadV2.start();
    	});
    	
    	final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(3);
    	
    	//Start an Account sync thread in 3 minutes
    	//Why 20? No reason really, just to offset each Thread a bit to spread load
    	Thread accountThread = new Thread(new EspoAccountSyncRunnableV2());
    	scheduler.schedule(() -> accountThread.start(), 20, TimeUnit.MINUTES);
    	
    	//Start a Contact sync thread in 6 minutes
    	//Why 40? No reason really, just to offset each Thread a bit to spread load
    	Thread contactThread = new Thread(new EspoContactSyncRunnableV2());
    	scheduler.schedule(() -> contactThread.start(), 40, TimeUnit.MINUTES);
    	
		//Start the Spring boot server
		App.logInfo("Starting Spring Boot server.");
    	SpringApplication.run(App.class, args);
    }
    
    public static SqlManager getSqlManager() {
    	return App.sqlManager;
    }
    
    public static Environment getEnvironment() {
    	return App.environment;
    }
    
	public static void logDebug(Object log) {
		if(!DEBUG) return;
		
		//kk:mm:ss --> hour:minute:seconds, hours using 24hr clock
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println("[" + now.format(f) + "][DEBUG] " + log);
	}
	
	public static void logInfo(Object log) {
		//kk:mm:ss --> hour:minute:seconds, hours using 24hr clock
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println("[" + now.format(f) + "][INFO] " + log);
	}
	
	public static void logError(Object log) {
		//kk:mm:ss --> hour:minute:seconds, hours using 24hr clock
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.err.println("[" + now.format(f) + "][ERROR] " + log);
	}   
}
