package com.fuljo.polimi.middleware.pub_sub_delivered.shipping;

import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.Order;
import com.fuljo.polimi.middleware.pub_sub_delivered.model.avro.OrderState;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 * Kafka Streams Transformer to validate shipments
 * <p>
 * Takes in input a VALIDATED order and checks if:
 *     <ul>
 *         <li>its address matches a particular pattern</li>
 *     </ul>
 *     and transforms its state to SHIPPING or FAILED accordingly.
 * </p>
 *
 * @implNote Currently this operation is stateless, but we used a transformer nonetheless,
 * so it can be made useful in the future (i.e. access a state store).
 */
class ShipmentValidator implements ValueTransformer<Order, Order> {


    @Override
    public void init(ProcessorContext processorContext) {
        // Nothing to do
    }

    @Override
    public Order transform(Order order) {
        // Does the address match the pattern?
        boolean valid = ShippingService.ADDRESS_PATTERN.matcher(order.getShippingAddress()).matches();
        // Clone instance and set new state
        return Order.newBuilder(order)
                .setState(valid ? OrderState.SHIPPING : OrderState.FAILED)
                .build();
    }

    @Override
    public void close() {
        // Nothing to do
    }
}
