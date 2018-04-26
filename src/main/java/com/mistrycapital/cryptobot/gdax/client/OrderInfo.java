package com.mistrycapital.cryptobot.gdax.client;

import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.OrderType;
import com.mistrycapital.cryptobot.gdax.common.Product;

import java.util.UUID;

import static com.mistrycapital.cryptobot.gdax.client.JsonObjectUtil.getOrDefault;
import static com.mistrycapital.cryptobot.gdax.client.JsonObjectUtil.parseTimeMicros;

/** Stores information about an order placed, as returned when placing an order or checking it */
public class OrderInfo {
	/** Order id */
	private final UUID orderId;
	/** Price */
	private final double price;
	/** Size */
	private final double size;
	/** Product */
	private final Product product;
	/** Order side */
	private final OrderSide side;
	/** Self trade prevention */
	private final SelfTradePreventionFlag selfTradePreventionFlag;
	/** Order type */
	private final OrderType type;
	/** Time in force */
	private final TimeInForce timeInForce;
	/** True if post only on limit order */
	private final boolean postOnly;
	/** UTC time in microseconds when order was created */
	private final long timeMicros;
	/** Fill fees */
	private final double fillFees;
	/** Filled size */
	private final double filledSize;
	/** Executed value */
	private final double executedValue;
	/** Status */
	private final OrderStatus status;
	/** True if settled */
	private final boolean settled;

	public OrderInfo(JsonObject json) {
		orderId = UUID.fromString(json.get("id").getAsString());
		price = getOrDefault(json, "price", Double.NaN);
		size = getOrDefault(json, "size", Double.NaN);
		product = Product.parse(json.get("product_id").getAsString());
		side = OrderSide.parse(json.get("side").getAsString());
		selfTradePreventionFlag = SelfTradePreventionFlag.parse(json.get("stp").getAsString());
		type = OrderType.parse(json.get("type").getAsString());
		timeInForce = json.has("time_in_force")
			? TimeInForce.parse(json.get("time_in_force").getAsString()) : TimeInForce.defaultValue;
		postOnly = getOrDefault(json, "post_only", false);
		timeMicros = parseTimeMicros(json.get("created_at").getAsString());
		fillFees = getOrDefault(json, "fill_fees", Double.NaN);
		filledSize = getOrDefault(json, "filled_size", Double.NaN);
		executedValue = getOrDefault(json, "executed_value", Double.NaN);
		status = OrderStatus.parse(json.get("status").getAsString());
		settled = getOrDefault(json, "settled", false);
	}

	/** @return Order id */
	public final UUID getOrderId() {
		return orderId;
	}

	/** @return Price */
	public final double getPrice() {
		return price;
	}

	/** @return Size */
	public final double getSize() {
		return size;
	}

	/** @return Product */
	public final Product getProduct() {
		return product;
	}

	/** @return Order side */
	public final OrderSide getOrderSide() {
		return side;
	}

	/** @return Self trade prevention */
	public final SelfTradePreventionFlag getSelfTradePreventionFlag() {
		return selfTradePreventionFlag;
	}

	/** @return Order type */
	public final OrderType getType() {
		return type;
	}

	/** @return Time in force */
	public final TimeInForce getTimeInForce() {
		return timeInForce;
	}

	/** @return True if post only on limit order */
	public final boolean isPostOnly() {
		return postOnly;
	}

	/** @return UTC time in microseconds when order was created */
	public final long getTimeMicros() {
		return timeMicros;
	}

	/** @return Fill fees */
	public final double getFillFees() {
		return fillFees;
	}

	/** @return Filled size */
	public final double getFilledSize() {
		return filledSize;
	}

	/** @return Executed value */
	public final double getExecutedValue() {
		return executedValue;
	}

	/** @return Status */
	public final OrderStatus getStatus() {
		return status;
	}

	/** @return True if settled */
	public final boolean isSettled() {
		return settled;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Order\t");
		builder.append(orderId);
		builder.append("\nprice\t");
		builder.append(price);
		builder.append("\nsize\t");
		builder.append(size);
		builder.append("\nproduct\t");
		builder.append(product);
		builder.append("\nside\t");
		builder.append(side);
		builder.append("\nselfTradePreventionFlag\t");
		builder.append(selfTradePreventionFlag);
		builder.append("\ntype\t");
		builder.append(type);
		builder.append("\ntimeInForce\t");
		builder.append(timeInForce);
		builder.append("\npostOnly\t");
		builder.append(postOnly);
		builder.append("\ntimeMicros\t");
		builder.append(timeMicros);
		builder.append("\nfillFees\t");
		builder.append(fillFees);
		builder.append("\nfilledSize\t");
		builder.append(filledSize);
		builder.append("\nexecutedValue\t");
		builder.append(executedValue);
		builder.append("\nstatus\t");
		builder.append(status);
		builder.append("\nsettled\t");
		builder.append(settled);
		return builder.toString();
	}
}
