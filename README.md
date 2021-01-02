# Orbit Sample - Compose Stock List

This sample implements a stock list using [Orbit MVI](https://github.com/orbit-mvi/orbit-mvi).

- The application uses Dagger Hilt for dependency injection which is initialised
  in [StockListApplication](app/src/main/kotlin/org/orbitmvi/orbit/sample/stocklist/StockListApplication.kt).

- Streaming data is provided by [Lightstreamer](https://lightstreamer.com) and
  their demo server with callback interfaces converted to Kotlin Flow's with
  [callbackFlow](https://kotl