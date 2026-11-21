package com.example.demo.checkout.converter;

import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.checkout.calculator.CheckoutCalculator;
import com.example.demo.checkout.dto.response.CheckoutItemReceipt;
import com.example.demo.checkout.dto.response.CheckoutOrderReceipt;
import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;
import com.example.demo.checkout.dto.response.SkippedCheckoutItem;
import com.example.demo.order.dto.response.CreatedOrderSeed;
import com.example.demo.order.entity.OrderStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class CheckoutConverter {
    private final JsonMapper json = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNumberLength(CheckoutCalculator.MAX_INTEGER_DIGITS).build()).build()).build();

    public CheckoutReceiptResponse toReceipt(CreatedOrderSeed seed, List<SkippedCheckoutItem> skipped) {
        List<CheckoutItemReceipt> items = seed.getItems().stream().map(item -> new CheckoutItemReceipt(item.getId(),
                item.getSnapshot().getProductName(),item.getSnapshot().getSku(),item.getSnapshot().getOptions(),
                item.getSnapshot().getQuantity(),new SellingPrice(item.getSnapshot().getUnitPrice(),"VND"),
                new SellingPrice(item.getSnapshot().getLineTotal(),"VND"))).toList();
        CheckoutOrderReceipt header = new CheckoutOrderReceipt(seed.getId(),seed.getOrderNumber(),seed.getCommand().getPlacedAt(),
                OrderStatus.PENDING,seed.getCommand().getRecipientName(),seed.getCommand().getRecipientPhone(),seed.getCommand().getDeliveryAddress(),items,
                new SellingPrice(seed.getCommand().getMerchandiseTotal(),"VND"),new SellingPrice(seed.getCommand().getPayableTotal(),"VND"));
        return new CheckoutReceiptResponse(new com.example.demo.checkout.dto.response.CheckoutOrderReceiptV2(header,
                new SellingPrice(seed.getCommand().getVoucherDiscount(),"VND"),seed.getCommand().getVoucherSnapshot()),skipped);
    }

    public String encode(CheckoutReceiptResponse receipt) {
        ObjectNode root = json.valueToTree(receipt);
        ((ObjectNode)root.get("order")).put("placedAt",receipt.getOrder().getPlacedAt().toString());
        return json.writeValueAsString(root);
    }

    public CheckoutReceiptResponse decode(String body) {
        JsonNode root = json.readTree(body);
        return decode(body, root.get("order").has("voucherDiscount") ? 2 : 1);
    }
    public CheckoutReceiptResponse decode(String body, int version) {
        if (version!=1 && version!=2) throw new IllegalStateException("Unsupported stored checkout receipt");
        JsonNode root = json.readTree(body);
        JsonNode order = root.get("order");
        if ((version==1 && (order.has("voucherDiscount") || order.has("voucher")))
            || (version==2 && (!order.has("voucherDiscount") || !order.has("voucher")))) throw new IllegalStateException("Receipt version/shape mismatch");
        if (!"PENDING".equals(order.get("status").asText())) throw new IllegalStateException("Invalid immutable receipt state");
        List<CheckoutItemReceipt> items = new ArrayList<>();
        for (JsonNode item : order.get("items")) {
            Map<String,String> options = new HashMap<>();
            item.get("variantOptionsSnapshot").properties().forEach(entry -> options.put(entry.getKey(),entry.getValue().asText()));
            items.add(new CheckoutItemReceipt(item.get("id").longValue(),item.get("productNameSnapshot").asText(),
                    item.get("variantSkuSnapshot").asText(),options,item.get("quantity").intValue(),
                    money(item.get("unitPriceSnapshot")),money(item.get("lineTotal"))));
        }
        List<SkippedCheckoutItem> skipped = new ArrayList<>();
        for (JsonNode item : root.get("skippedItems")) {
            skipped.add(new SkippedCheckoutItem(item.get("cartItemId").longValue(),SkippedCheckoutItem.Reason.valueOf(item.get("reason").asText())));
        }
        CheckoutOrderReceipt header = new CheckoutOrderReceipt(order.get("id").longValue(),order.get("orderNumber").asText(),
                Instant.parse(order.get("placedAt").asText()),OrderStatus.PENDING,order.get("recipientNameSnapshot").asText(),
                order.get("recipientPhoneSnapshot").asText(),order.get("deliveryAddressSnapshot").asText(),items,
                money(order.get("merchandiseTotal")),money(order.get("payableTotal")));
        if (version==2) {
            var snapshot=order.get("voucher").isNull() ? null : json.treeToValue(order.get("voucher"),com.example.demo.voucher.dto.response.VoucherSnapshot.class);
            header=new com.example.demo.checkout.dto.response.CheckoutOrderReceiptV2(header,money(order.get("voucherDiscount")),snapshot);
        }
        return new CheckoutReceiptResponse(header,skipped);
    }

    private SellingPrice money(JsonNode value) {
        return new SellingPrice(value.get("amount").bigIntegerValue(),value.get("currency").asText());
    }
}
