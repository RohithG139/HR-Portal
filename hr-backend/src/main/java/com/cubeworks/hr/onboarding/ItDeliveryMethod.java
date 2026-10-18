package com.cubeworks.hr.onboarding;

/**
 * How IT equipment is to be delivered to the new employee.
 *
 * <p>Stored as {@code varchar(20)} with a CHECK constraint in
 * {@code hr.onboarding.it_delivery_method}.</p>
 */
public enum ItDeliveryMethod {
    /** Employee collects the equipment on their first day at the office. */
    OFFICE_DAY1,
    /** Equipment is shipped to the employee's home address before the start date. */
    SHIP_HOME
}
