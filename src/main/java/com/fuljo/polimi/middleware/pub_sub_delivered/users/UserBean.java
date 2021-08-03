package com.fuljo.polimi.middleware.pub_sub_delivered.users;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.User;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.UserRole;

import java.util.Objects;

/**
 * User representation for the REST API
 */
public class UserBean {

    public enum Role { CUSTOMER, ADMIN, DELIVERY }

    /**
     * User identifier
     */
    private final String id;
    /**
     * Full name
     */
    private final String name;
    /**
     * Email
     */
    private final String email;
    /**
     * Role
     */
    private final Role role;

    @JsonCreator
    public UserBean(@JsonProperty("id") String id,
                    @JsonProperty("name") String name,
                    @JsonProperty("email") String email,
                    @JsonProperty("role") Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    @JsonGetter("id")
    public String getId() {
        return id;
    }

    @JsonGetter("name")
    public String getName() {
        return name;
    }

    @JsonGetter("email")
    public String getEmail() {
        return email;
    }

    @JsonGetter("role")
    public Role getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserBean userBean = (UserBean) o;
        return id.equals(userBean.id) && name.equals(userBean.name) && email.equals(userBean.email)
                && role.equals(userBean.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, email, role);
    }

    public static UserBean toBean(User user) {
        return new UserBean(user.getId().toString(), user.getName().toString(), user.getEmail().toString(),
                Role.valueOf(user.getRole().name()));
    }

    public static User fromBean(UserBean bean) {
        return new User(bean.getId(), bean.getName(), bean.getEmail(), UserRole.valueOf(bean.getRole().name()));
    }
}
