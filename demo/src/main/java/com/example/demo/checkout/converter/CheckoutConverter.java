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
        List<CheckoutItemReceipt> items = seed.getItems().stream().map(item -> {
            var snapshot = item.getSnapshot();
            var base = new CheckoutItemReceipt(item.getId(),snapshot.getProductName(),snapshot.getSku(),snapshot.getOptions(),
                    snapshot.getQuantity(),new SellingPrice(snapshot.getUnitPrice(),"VND"),new SellingPrice(snapshot.getLineTotal(),"VND"));
            var pricing = java.util.Objects.requireNonNull(snapshot.getPricing(), "New checkout requires prepared pricing");
            return (CheckoutItemReceipt)new com.example.demo.checkout.dto.response.CheckoutItemReceiptV3(base,
                    pricing.getOriginalPrice(),pricing.getDirectUnitDiscount(),pricing.getSalePercentage()==null ? null :
                    new com.example.demo.order.dto.response.DirectSaleSnapshot(pricing.getSalePercentage(),pricing.getSaleStartsAt(),pricing.getSaleEndsAt()),pricing.getPricedAt());
        }).toList();
        CheckoutOrderReceipt header = new CheckoutOrderReceipt(seed.getId(),seed.getOrderNumber(),seed.getCommand().getPlacedAt(),
                OrderStatus.PENDING,seed.getCommand().getRecipientName(),seed.getCommand().getRecipientPhone(),seed.getCommand().getDeliveryAddress(),items,
                new SellingPrice(seed.getCommand().getMerchandiseTotal(),"VND"),new SellingPrice(seed.getCommand().getPayableTotal(),"VND"));
        return new CheckoutReceiptResponse(new com.example.demo.checkout.dto.response.CheckoutOrderReceiptV2(header,
                new SellingPrice(seed.getCommand().getVoucherDiscount(),"VND"),seed.getCommand().getVoucherSnapshot()),skipped);
    }

    public String encode(CheckoutReceiptResponse receipt) {
        ObjectNode root = json.valueToTree(receipt);
        ((ObjectNode)root.get("order")).put("placedAt",receipt.getOrder().getPlacedAt().toString());
        for (int i=0;i<receipt.getOrder().getItems().size();i++) {
            if (receipt.getOrder().getItems().get(i) instanceof com.example.demo.checkout.dto.response.CheckoutItemReceiptV3 item) {
                ObjectNode node = (ObjectNode)root.get("order").get("items").get(i);
                node.put("pricingInstantSnapshot",item.getPricingInstantSnapshot().toString());
                if (item.getDirectSaleSnapshot()!=null) {
                    ObjectNode sale = (ObjectNode)node.get("directSaleSnapshot");
                    if (item.getDirectSaleSnapshot().getStartsAt()!=null) sale.put("startsAt",item.getDirectSaleSnapshot().getStartsAt().toString());
                    if (item.getDirectSaleSnapshot().getEndsAt()!=null) sale.put("endsAt",item.getDirectSaleSnapshot().getEndsAt().toString());
                }
            }
        }
        return json.writeValueAsString(root);
    }

    public CheckoutReceiptResponse decode(String body) {
        JsonNode root = json.readTree(body);
        return decode(body, root.get("order").get("items").get(0).has("pricingInstantSnapshot") ? 3 : root.get("order").has("voucherDiscount") ? 2 : 1);
    }
    public CheckoutReceiptResponse decode(String body, int version) {
        if (version!=1 && version!=2 && version!=3) throw new IllegalStateException("Unsupported stored checkout receipt");
        JsonNode root = json.readTree(body);
        JsonNode order = root.get("order");
        if ((version==1 && (order.has("voucherDiscount") || order.has("voucher")))
            || (version>=2 && (!order.has("voucherDiscount") || !order.has("voucher")))) throw new IllegalStateException("Receipt version/shape mismatch");
        if (!"PENDING".equals(order.get("status").asText())) throw new IllegalStateException("Invalid immutable receipt state");
        List<CheckoutItemReceipt> items = new ArrayList<>();
        for (JsonNode item : order.get("items")) {
            Map<String,String> options = new HashMap<>();
            item.get("variantOptionsSnapshot").properties().forEach(entry -> options.put(entry.getKey(),entry.getValue().asText()));
            CheckoutItemReceipt decoded = new CheckoutItemReceipt(item.get("id").longValue(),item.get("productNameSnapshot").asText(),
                    item.get("variantSkuSnapshot").asText(),options,item.get("quantity").intValue(),
                    money(item.get("unitPriceSnapshot")),money(item.get("lineTotal")));
            if (version==3) {
                for (String required : List.of("originalUnitPriceSnapshot","directUnitDiscountSnapshot","directSaleSnapshot","pricingInstantSnapshot"))
                    if (!item.has(required)) throw new IllegalStateException("Incomplete v3 receipt evidence");
                JsonNode sale = item.get("directSaleSnapshot");
                decoded = new com.example.demo.checkout.dto.response.CheckoutItemReceiptV3(decoded,
                        money(item.get("originalUnitPriceSnapshot")),money(item.get("directUnitDiscountSnapshot")),sale.isNull() ? null :
                        new com.example.demo.order.dto.response.DirectSaleSnapshot(sale.get("percentage").decimalValue(),
                                instant(sale.get("startsAt")),instant(sale.get("endsAt"))),instant(item.get("pricingInstantSnapshot")));
            } else if (item.has("pricingInstantSnapshot") || item.has("originalUnitPriceSnapshot") || item.has("directUnitDiscountSnapshot") || item.has("directSaleSnapshot")) {
                throw new IllegalStateException("Receipt version/shape mismatch");
            }
            items.add(decoded);
        }
        List<SkippedCheckoutItem> skipped = new ArrayList<>();
        for (JsonNode item : root.get("skippedItems")) {
            skipped.add(new SkippedCheckoutItem(item.get("cartItemId").longValue(),SkippedCheckoutItem.Reason.valueOf(item.get("reason").asText())));
        }
        CheckoutOrderReceipt header = new CheckoutOrderReceipt(order.get("id").longValue(),order.get("orderNumber").asText(),
                Instant.parse(order.get("placedAt").asText()),OrderStatus.PENDING,order.get("recipientNameSnapshot").asText(),
                order.get("recipientPhoneSnapshot").asText(),order.get("deliveryAddressSnapshot").asText(),items,
                money(order.get("merchandiseTotal")),money(order.get("payableTotal")));
        if (version>=2) {
            var snapshot=order.get("voucher").isNull() ? null : json.treeToValue(order.get("voucher"),com.example.demo.voucher.dto.response.VoucherSnapshot.class);
            header=new com.example.demo.checkout.dto.response.CheckoutOrderReceiptV2(header,money(order.get("voucherDiscount")),snapshot);
        }
        return new CheckoutReceiptResponse(header,skipped);
    }

    private Instant instant(JsonNode value) { return value == null || value.isNull() ? null : Instant.parse(value.asText()); }

    private SellingPrice money(JsonNode value) {
        return new SellingPrice(value.get("amount").bigIntegerValue(),value.get("currency").asText());
    }
}
