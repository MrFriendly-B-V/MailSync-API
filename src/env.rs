use espocrm_rs::EspoApiClient;
use mysql::{OptsBuilder, Pool};
use serde::Deserialize;
use anyhow::Result;

#[derive(Deserialize, Clone)]
pub struct Env {
    espo_api_key:       String,
    espo_api_secret:    String,
    pub espo_host:          String,
    pub authlander_key:     String,
    pub authlander_host:    String,
    mysql_host:         String,
    mysql_database:     String,
    mysql_username:     String,
    mysql_password:     String
}

#[derive(Clone)]
pub struct AppData {
    pub pool:       Pool,
    pub espo:       EspoApiClient,
    pub env:        Env
}

mod migrations {
    use refinery::embed_migrations;
    embed_migrations!("./migrations");
}

impl AppData {
    pub fn new(env: &Env) -> Result<Self> {
        let options = OptsBuilder::new()
            .ip_or_hostname(Some(&env.mysql_host))
            .user(Some(&env.mysql_username))
            .pass(Some(&env.mysql_password))
            .db_name(Some(&env.mysql_database));
        let pool = mysql::Pool::new(options)?;

        let espo = EspoApiClient::new(&env.espo_host)
            .set_api_key(&env.espo_api_key)
            .set_secret_key(&env.espo_api_secret)
            .build();

        Ok(Self {
            pool,
            espo,
            env: env.clone()
        })
    }

    pub fn migrate(&self) -> Result<()> {
        let mut conn = self.pool.get_conn()?;
        migrations::migrations::runner().run(&mut conn)?;
        Ok(())
    }
}