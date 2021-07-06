
/*
 * Copyright 2021 Mikołaj Leszczyński & Appmattus Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.orbitmvi.orbit.sample.stocklist.streaming.stock

import android.text.format.DateUtils
import com.lightstreamer.client.ItemUpdate
import com.lightstreamer.client.Subscription
import com.lightstreamer.client.SubscriptionListener
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.orbitmvi.orbit.sample.stocklist.streaming.EmptySubscriptionListener
import org.orbitmvi.orbit.sample.stocklist.streaming.StreamingClient

@Suppress("MagicNumber", "ComplexCondition")
class StockRepository(private val client: StreamingClient) {

    private val items = (1..20).map { "item$it" }.toTypedArray()
    private val subscriptionFields = arrayOf("stock_name", "bid", "ask", "timestamp")
    private val detailSubscriptionFields =
        arrayOf("stock_name", "timestamp", "pct_change", "bid_quantity", "bid", "ask", "ask_quantity", "min", "max")

    @Suppress("EXPERIMENTAL_API_USAGE")
    fun stockList(): Flow<List<Stock>> = callbackFlow {
        val stockList = MutableList<Stock?>(20) { null }

        val bidJobs = mutableMapOf<Int, Job?>()
        val askJobs = mutableMapOf<Int, Job?>()

        trySend(emptyList())

        val subscription = Subscription("MERGE", items, subscriptionFields).apply {
            dataAdapter = "QUOTE_ADAPTER"
            requestedMaxFrequency = "1"
            requestedSnapshot = "yes"
            addListener(
                object : SubscriptionListener by EmptySubscriptionListener {
                    override fun onItemUpdate(p0: ItemUpdate) {
                        val itemName = p0.itemName
                        val stockName = p0.getValue("stock_name")
                        val formattedBid = p0.getValue("bid")?.to2dp()
                        val formattedAsk = p0.getValue("ask")?.to2dp()
                        val formattedTimestamp = p0.getValue("timestamp")?.toFormattedTimestamp()

                        if (itemName != null && stockName != null && formattedBid != null && formattedAsk != null &&
                            formattedTimestamp != null
                        ) {
                            val bidTick = tickDirection(stockList[p0.itemPos - 1]?.bid, formattedBid)
                            val askTick = tickDirection(stockList[p0.itemPos - 1]?.ask, formattedAsk)

                            stockList[p0.itemPos - 1] = stockList[p0.itemPos - 1]?.copy(
                                bid = formattedBid,
                                ask = formattedAsk,
                                timestamp = formattedTimestamp
                            ) ?: Stock(itemName, stockName, formattedBid, null, formattedAsk, null, formattedTimestamp)

                            bidTick?.let {
                                bidJobs[p0.itemPos]?.cancel()

                                stockList[p0.itemPos - 1] = stockList[p0.itemPos - 1]?.copy(bidTick = bidTick)