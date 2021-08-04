package com.fuljo.polimi.middleware.pub_sub_delivered.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.Product;

import java.util.Objects;

/**
 * Product representation for the REST API
 */
public class ProductBean {

    /**
     * Machine-friendly identifier
     */
    private final String id;
    /**
     * User-friendly name
     */
    private final String name;
    /**
     * User-friendly description
     */
    private final String description;
    /**
     * Unitary price
     */
    private final double price;
    /**
     * Is the product actually available for purchase?
     */
    private final boolean available;

    @JsonCreator
    public ProductBean(@JsonProperty("id") String id,
                       @JsonProperty("name") String name,
                       @JsonProperty("description") String description,
                       @JsonProperty("price") double price,
                       @JsonProperty("available") boolean available) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.available = available;
    }

    @JsonGetter("id")
    public String getId() {
        return id;
    }

    @JsonGetter("name")
    public String getName() {
        return name;
    }

    @JsonGetter("description")
    public String getDescription() {
        return description;
    }

    @JsonGetter("price")
    public double getPrice() {
        return price;
    }

    @JsonGetter("available")
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductBean that = (ProductBean) o;
        return Double.compare(that.price, price) == 0 && available == that.available && id.equals(that.id) && name.equals(that.name) && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, price, available);
    }

    public static ProductBean toBean(Product product) {
        return new ProductBean(
                product.getId().toString(),
                product.getName().toString(),
                product.getDescription().toString(),
                product.getPrice(),
                product.getAvailable()
        );
    }

    public static Product fromBean(ProductBean bean) {
        return new Product(
                bean.getId(),
                bean.getName(),
                bean.getDescription(),
                bean.getPrice(),
                bean.isAvailable()
        );
    }
}
