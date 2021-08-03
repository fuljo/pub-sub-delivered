package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;

public class ServiceUtils {

    public static final String AUTH_COOKIE = "psd_auth";

    /* NOTE: This is not safe to use in production, this is just a demo */
    private static final String SECRET_TOKEN = "U29tZWJvZHkgb25jZSB0b2xkIG1lIHRoZSB3b3JsZCB3YXMgZ29ubmEgcm9sbCBtZQ";

    /**
     * Generates the token for this user
     *
     * @param userId user id
     * @return the token
     */
    public static String generateUserToken(String userId) {
        return String.format("%s:%s", userId, SECRET_TOKEN);
    }

    /**
     * Verifies a user's token and returns the user, if successful
     * <p>
     * Note that this function does not check whether the user exists or not
     *
     * @param token the token as set in the cookie
     * @return the user's name if the token is valid, null otherwise
     */
    public static String verifyUserToken(String token) {
        String[] split = token.split(":", 2);
        if (split[1].equals(SECRET_TOKEN)) { // successful
            return split[0];
        } else {
            return null;
        }
    }

    /**
     * Generates an authentication cookie for a specific user
     *
     * @param userId user id
     * @return the new cookie
     */
    public static NewCookie generateAuthCookie(String userId) {
        return new NewCookie(AUTH_COOKIE, generateUserToken(userId));
    }

    /**
     * Verifies an authentication cookie and returns the user, if successful
     * <p>
     * Note that this function does not check whether the user exists or not
     *
     * @param cookie the cookie from the request
     * @return the user's name if the token is valid, null otherwise
     */
    public static String verifyAuthCookie(Cookie cookie) {
        if (cookie.getName().equals(AUTH_COOKIE)) {
            return verifyUserToken(cookie.getValue());
        } else {
            return null;
        }
    }
}
