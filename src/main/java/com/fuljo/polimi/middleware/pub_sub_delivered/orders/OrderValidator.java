package com.fuljo.polimi.middleware.pub_sub_delivered.orders;

import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.Order;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.OrderState;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.Product;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka Streams Transformer to validate orders
 * <p>
 * Takes in input a CREATED order and checks if:
 *     <ul>
 *         <li>It holds at least one product</li>
 *         <li>All of its products are available</li>
 *     </ul>
 *     and transforms the order's state to VALIDATED or FAILED accordingly.
 * </p>
 * A global products store with name {@link OrdersService#PRODUCTS_STORE_NAME} must be present in the same streams instance.
 */
class OrderValidator implements ValueTransformer<Order, Order> {

    private static final Logger log = LoggerFactory.getLogger(OrderValidator.class);

    private ReadOnlyKeyValueStore<String, ValueAndTimestamp<Product>> productsStore;

    @Override
    public void init(ProcessorContext context) {
        productsStore = context.getStateStore(OrdersService.PRODUCTS_STORE_NAME);
    }

    @Override
    public Order transform(Order order) {
        log.debug("Processing order {}", order.getId());
        Boolean valid = order.getProducts().keySet().stream()
                // map each product to its availability value
                .map(productId -> getProductAvailability(productId.toString()))
                // all products must be available
                .reduce(Boolean::logicalAnd)
                // at least one product must be present
                .orElse(false);
        // Clone the order and change the state
        return Order.newBuilder(order)
                .setState(valid ? OrderState.VALIDATED : OrderState.FAILED)
                .build();
    }

    private boolean getProductAvailability(String productId) {
        ValueAndTimestamp<Product> vt = productsStore.get(productId);
        if (vt == null || vt.value() == null) {
            return false;
        } else {
            return vt.value().getAvailable();
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }
}
