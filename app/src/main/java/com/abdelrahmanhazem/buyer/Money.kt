package com.abdelrahmanhazem.buyer

import java.util.Locale

/** Formats a price the same way everywhere: "EGP 35" or "EGP 12.50". */
fun egp(amount: Double): String =
    if (amount % 1.0 == 0.0) "EGP ${amount.toLong()}"
    else String.format(Locale.US, "EGP %.2f", amount)