package nl.mrfriendly.espogmailsync;

public class Environment {

	private String authApiToken, authServerHost, frontendHost, espoHost, espoApiKey, espoSecretKey, mysqlHost, mysqlDb, mysqlUsername, mysqlPassword;

	/**
	 * @return the apiToken
	 */
	public String getAuthApiToken() {
		return authApiToken;
	}

	/**
	 * @param apiToken the apiToken to set
	 */
	public void setAuthApiToken(String apiToken) {
		this.authApiToken = apiToken;
	}

	/**
	 * @return the authServerHost
	 */
	public String getAuthServerHost() {
		return authServerHost;
	}

	/**
	 * @param authServerHost the authServerHost to set
	 */
	public void setAuthServerHost(String authServerHost) {
		this.authServerHost = authServerHost;
	}

	/**
	 * @return the frontendHost
	 */
	public String getFrontendHost() {
		return frontendHost;
	}

	/**
	 * @param frontendHost the frontendHost to set
	 */
	public void setFrontendHost(String frontendHost) {
		this.frontendHost = frontendHost;
	}

	/**
	 * @return the espoHost
	 */
	public String getEspoHost() {
		return espoHost;
	}

	/**
	 * @param espoHost the espoHost to set
	 */
	public void setEspoHost(String espoHost) {
		this.espoHost = espoHost;
	}

	/**
	 * @return the espoApiKey
	 */
	public String getEspoApiKey() {
		return espoApiKey;
	}

	/**
	 * @param espoApiKey the espoApiKey to set
	 */
	public void setEspoApiKey(String espoApiKey) {
		this.espoApiKey = espoApiKey;
	}

	/**
	 * @return the espoSecretKey
	 */
	public String getEspoSecretKey() {
		return espoSecretKey;
	}

	/**
	 * @param espoSecretKey the espoSecretKey to set
	 */
	public void setEspoSecretKey(String espoSecretKey) {
		this.espoSecretKey = espoSecretKey;
	}

	/**
	 * @return the mysqlHost
	 */
	public String getMysqlHost() {
		return mysqlHost;
	}

	/**
	 * @param mysqlHost the mysqlHost to set
	 */
	public void setMysqlHost(String mysqlHost) {
		this.mysqlHost = mysqlHost;
	}

	/**
	 * @return the mysqlDb
	 */
	public String getMysqlDb() {
		return mysqlDb;
	}

	/**
	 * @param mysqlDb the mysqlDb to set
	 */
	public void setMysqlDb(String mysqlDb) {
		this.mysqlDb = mysqlDb;
	}

	/**
	 * @return the mysqlUsername
	 */
	public String getMysqlUsername() {
		return mysqlUsername;
	}

	/**
	 * @param mysqlUsername the mysqlUsername to set
	 */
	public void setMysqlUsername(String mysqlUsername) {
		this.mysqlUsername = mysqlUsername;
	}

	/**
	 * @return the mysqlPassword
	 */
	public String getMysqlPassword() {
		return mysqlPassword;
	}

	/**
	 * @param mysqlPassword the mysqlPassword to set
	 */
	public void setMysqlPassword(String mysqlPassword) {
		this.mysqlPassword = mysqlPassword;
	}
	
}
