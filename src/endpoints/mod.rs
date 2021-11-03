pub mod user;
pub mod mail;
pub mod settings;

#[macro_export]
macro_rules! authorization {
    ($req:expr) => {
        match $req.headers().get("authorization") {
            Some(authorization) => match authorization.to_str() {
                Ok(hv) => hv.to_string(),
                Err(_) => return Err($crate::error::Error::Unauthorized)
            },
            None => return Err($crate::error::Error::Unauthorized)
        }
    }
}

#[macro_export]
macro_rules! check_scopes {
    ($req:expr, $data:expr, $scopes_required:expr) => {
        {
            ::log::debug!("Getting Authorization header");
            // Get the value of the Authorization header
            let authorization: String = $crate::authorization!($req);

            if authorization.is_empty() {
                return Err($crate::error::Error::Unauthorized);
            }

            // Check if the session exists AND is active
            let session = ::authlander_client::Session::new(&authorization, &$data.env.authlander_host);

            ::log::debug!("Checking session");
            let scheck = session.check().await?;
            if !scheck.active || !scheck.session_valid {
                ::log::debug!("Session is not active or invalid");
                return Err($crate::error::Error::Unauthorized);
            }

            // Get the User
            ::log::debug!("Getting user");
            let user = match session.get_user().await? {
                Some(u) => u,
                None => {
                    ::log::debug!("User does not exist on Authlander server");
                    return Err($crate::error::Error::Unauthorized)
                }
            };

            // Check if the user has all scopes
            ::log::debug!("Getting scopes");
            let scopes = user.get_scopes().await?.scopes;
            if !$scopes_required.iter().all(|i| scopes.contains(&i.to_string())) {
                ::log::debug!("User does not have the required scopes");
                return Err($crate::error::Error::Unauthorized);
            }
        }
    }
}