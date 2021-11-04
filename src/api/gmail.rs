use anyhow::Result;
use crate::{RT, RQ};
use serde::{Deserialize, Serialize};
use crate::env::Env;
use crate::error::Error;

const PATH: &str = "https://gmail.googleapis.com/gmail/v1";

async fn get_user_token(env: &Env, user_id: &str) -> Result<String> {
    let user = authlander_client::User::new(user_id, &env.authlander_host);
    let token = user.token(&env.authlander_key)
        .await?;

    Ok(token.access_token.expect("Access token was None"))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Message {
    pub id:             String,
    pub thread_id:      String,
    pub label_ids:      Vec<String>,
    pub snippet:        Option<String>,
    pub history_id:     Option<String>,
    pub internal_date:  String,
    pub raw:            String
}

#[derive(Serialize)]
struct GetUserMessagesQuery {
    format: &'static str
}

pub async fn get_user_messages<A: AsRef<str>, B: AsRef<str>>(env: &Env, user_id: A, message_id: B) -> Result<Message> {
    let _guard = RT.enter();
    let message= RQ.get(format!("{}/users/{}/messages/{}", PATH, user_id.as_ref(), message_id.as_ref()))
        .header("Authorization", format!("Bearer {}", get_user_token(env, user_id.as_ref()).await?))
        .query(&GetUserMessagesQuery { format: "raw" })
        .send()
        .await?
        .json()
        .await?;

    Ok(message)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListUserMessages {
    pub messages:               Vec<SimpleMessage>,
    pub next_page_token:        Option<String>,
    pub result_size_estimate:   i32
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimpleMessage {
    pub id:         String,
}

#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ListUserMessagesQuery<'a> {
    pub max_results:    Option<i32>,
    pub page_token:     Option<&'a str>,
}

pub async fn list_user_messages<A: AsRef<str>>(env: &Env, user_id: A, query: &ListUserMessagesQuery<'_>) -> std::result::Result<ListUserMessages, Error> {
    let _guard = RT.enter();
    let req = RQ.get(format!("{}/users/{}/messages", PATH, user_id.as_ref()))
        .header("Authorization", format!("Bearer {}", get_user_token(env, user_id.as_ref()).await?))
        .query(query)
        .send()
        .await?;

    if req.status().as_u16() == 401 || req.status().as_u16() == 403 {
        log::warn!("User '{}' does not have the required scopes to allow MailSync to list their GMail messages.", user_id.as_ref());
        return Err(Error::Gmail);
    }

    let response = req
        .json()
        .await?;

    Ok(response)
}