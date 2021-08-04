package com.fuljo.polimi.middleware.pub_sub_delivered.microservices;

import com.fuljo.polimi.middleware.pub_sub_delivered.exceptions.WebServiceException;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.UserRole;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

public class AuthenticationHelper {

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
     * Generates an authentication cookie for a specific user
     *
     * @param userId user id
     * @return the new cookie
     */
    public static NewCookie generateAuthCookie(String userId) {
        return new NewCookie(AUTH_COOKIE, generateUserToken(userId),
                "/api", null, null, NewCookie.DEFAULT_MAX_AGE, false);
    }

    /**
     * Authenticates a user from an authentication cookie.
     *
     * @param userStore  key-value store to retrieve users
     * @param authCookie auth cookie
     * @return the user, if correctly authenticated, otherwise null
     * @throws WebServiceException if the user is not authenticated or does not have the desired role
     */
    public static User authenticateUser(ReadOnlyKeyValueStore<String, User> userStore, Cookie authCookie) {
        return authenticateUser(userStore, authCookie, null);
    }

    /**
     * Authenticates a user from an authentication cookie.
     * <p>
     * If a desired role is specified, the access is forbidden unless the user has that role
     *
     * @param userStore   key-value store to retrieve users
     * @param authCookie  auth cookie
     * @param desiredRole desired user role, may be null
     * @return the user, if correctly authenticated, otherwise null
     * @throws WebServiceException if the user is not authenticated or does not have the desired role
     */
    public static User authenticateUser(ReadOnlyKeyValueStore<String, User> userStore,
                                        Cookie authCookie,
                                        UserRole desiredRole) {
        try {
            // Check the name of the cookie and non-empty value
            if (authCookie == null || !authCookie.getName().equals(AUTH_COOKIE) || authCookie.getValue().length() == 0) {
                throw new
                        WebServiceException("You need to authenticate to use this endpoint", Response.Status.UNAUTHORIZED);
            }
            // Extract the user id and secret
            String[] split = authCookie.getValue().split(":", 2);
            String userId = split[0];
            String secret = split[1];
            // Check that the user exists
            User user = userStore.get(userId);
            if (user == null) {
                throw new WebServiceException("User does not exist", Response.Status.UNAUTHORIZED);
            }
            // Check the secret
            if (!SECRET_TOKEN.equals(secret)) {
                throw new WebServiceException("Invalid token", Response.Status.UNAUTHORIZED);
            }
            // Check the role
            if (desiredRole != null && !desiredRole.equals(user.getRole())) {
                throw new WebServiceException(
                        "You need to be a " + desiredRole + " to use this endpoint", Response.Status.FORBIDDEN);
            }
            return user;
        } catch (IndexOutOfBoundsException e) {
            throw new WebServiceException("Invalid token", Response.Status.UNAUTHORIZED);
        }
    }
}
