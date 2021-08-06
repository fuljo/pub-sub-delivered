package com.fuljo.polimi.middleware.pub_sub_delivered.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.Order;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.OrderState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Representation of an order for the REST API
 */
public class OrderBean {

    public enum State {
        /**
         * Created by the user, validation pending
         */
        CREATED,
        /**
         * Validated by the orders service
         */
        VALIDATED,
        /**
         * Validated by orders service, address is valid => ready to ship
         */
        SHIPPING,
        /**
         * Delivery man confirmed shipment to the customer
         */
        SHIPPED,
        /**
         * Validation failed
         */
        FAILED
    }

    /**
     * Order id
     */
    private final String id;
    /**
     * Id of the owner of this order
     */
    private final String customerId;
    /**
     * Shipping address
     */
    private final String shippingAddress;
    /**
     * State of this order
     */
    private final State state;
    /**
     * Map of products in the order.
     * &lt;productId, quantity&gt;
     */
    private final Map<String, Integer> products;
    /**
     * Total price of the order
     */
    private final Double totalPrice;

    @JsonCreator()
    public OrderBean(@JsonProperty("id") String id,
                     @JsonProperty("customerId") String customerId,
                     @JsonProperty("shippingAddress") String shippingAddress,
                     @JsonProperty("state") State state,
                     @JsonProperty("products") Map<String, Integer> products,
                     @JsonProperty("totalPrice") Double totalPrice) {
        this.id = id;
        this.shippingAddress = shippingAddress;
        this.customerId = customerId;
        this.state = state;
        this.products = products;
        this.totalPrice = totalPrice;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("customerId")
    public String getCustomerId() {
        return customerId;
    }

    @JsonProperty("shippingAddress")
    public String getShippingAddress() {
        return shippingAddress;
    }

    @JsonProperty("state")
    public State getState() {
        return state;
    }

    @JsonProperty("products")
    public Map<String, Integer> getProducts() {
        return products;
    }

    @JsonProperty("totalPrice")
    public Double getTotalPrice() {
        return totalPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderBean orderBean = (OrderBean) o;
        return Objects.equals(id, orderBean.id) && Objects.equals(customerId, orderBean.customerId) && Objects.equals(shippingAddress, orderBean.shippingAddress) && state == orderBean.state && Objects.equals(products, orderBean.products) && Objects.equals(totalPrice, orderBean.totalPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customerId, shippingAddress, state, products, totalPrice);
    }

    public static Order fromBean(OrderBean bean) {
        return new Order(
                bean.getId(),
                bean.getCustomerId(),
                bean.getShippingAddress(),
                OrderState.valueOf(bean.getState().name()),
                new HashMap<>(bean.getProducts()),
                bean.getTotalPrice()
        );
    }

    public static OrderBean toBean(Order order) {
        return new OrderBean(
                order.getId().toString(),
                order.getCustomerId().toString(),
                order.getShippingAddress().toString(),
                State.valueOf(order.getState().name()),
                order.getProducts()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue)),
                order.getTotalPrice()
        );
    }
}
