package com.fuljo.polimi.middleware.pub_sub_delivered.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Representation of a newly created order for the REST API.
 *
 * This representation should be user when the user submits
 * the order for the first time, as it lacks:
 * <ul>
 *     <li>The order id, which will be generated by the backend</li>
 *     <li>The customer id, which will be derived from the authentication cookie of the user</li>
 *     <li>The shipping address, which will be retrieved from the user</li>
 *     <li>The total price, which will be calculated by the backend from the unitary prices of the items</li>
 * </ul>
 * All these missing information will be filled at the moment of submission: all orders stored in the topics will
 * always have all the correct fields.
 */
public class NewOrderBean {
    /**
     * Map of products in the order.
     * &lt;productId, quantity&gt;
     */
    private final Map<String, Integer> products;

    @JsonCreator
    public NewOrderBean(@JsonProperty("products") Map<String, Integer> products) {
        this.products = products;
    }

    @JsonGetter("products")
    public Map<String, Integer> getProducts() {
        return products;
    }
}
