package com.fuljo.polimi.middleware.pub_sub_delivered.model.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;

import java.util.Objects;

/**
 * User representation for the REST API
 */
public class UserBean {

    private final String id;
    private final String address;

    @JsonCreator
    public UserBean(@JsonProperty("id") String id, @JsonProperty("address") String address) {
        this.id = id;
        this.address = address;
    }

    @JsonGetter("id")
    public String getId() {
        return id;
    }

    @JsonGetter("address")
    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserBean userBean = (UserBean) o;
        return id.equals(userBean.id) && address.equals(userBean.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, address);
    }

    public static UserBean toBean(final User user) {
        return new UserBean(user.getId().toString(), user.getAddress().toString());
    }

    public static User fromBean(final UserBean user) {
        return new User(user.getId(), user.getAddress());
    }
}
