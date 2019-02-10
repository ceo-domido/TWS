

import java.util.*;

import com.ib.client.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.text.SimpleDateFormat;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

//! [ewrapperimpl]
public class EWrapperImpl implements EWrapper {
    //! [ewrapperimpl]

    public HashMap<HistoryItem, TreeMap<Calendar, Bar>> history = new HashMap();
    public HashMap<Integer, HistoryItem> requests = new HashMap<>();
    MongoCollection<Document> hystoricalData = null;
    MongoCollection<Document> symbols = null;
    MongoDatabase database = null;
    public ArrayList<Contract> contracts = new ArrayList();
    public ArrayList<Contract> portfolio = new ArrayList();
    public ArrayList<Indicator> indicators = new ArrayList();


    //! [socket_declare]
    private EReaderSignal readerSignal;
    private EClientSocket clientSocket;
    protected int currentOrderId = -1;
    //! [socket_declare]

    //! [socket_init]
    public EWrapperImpl() {
        readerSignal = new EJavaSignal();
        clientSocket = new EClientSocket(this, readerSignal);
    }

    //! [socket_init]
    public EClientSocket getClient() {
        return clientSocket;
    }

    public EReaderSignal getSignal() {
        return readerSignal;
    }

    public int getCurrentOrderId() {
        return currentOrderId;
    }


    public void loadState() {
        // Load collection of contracts system works with
        if (database != null) {
            System.out.println("Loading collection of contracts system works with...");
            MongoCursor<Document> cursor = database.getCollection("contracts").find().iterator();
            try {
                while (cursor.hasNext()) {
                    Document next = cursor.next();
                    Contract contract = new Contract();
                    contract.symbol(next.getString("symbol"));
                    contract.secType(next.getString("secType"));
                    contract.currency(next.getString("currency"));
                    contract.exchange(next.getString("exchange"));
                    //Specify the Primary Exchange attribute to avoid contract ambiguity
                    contract.primaryExch(next.getString("primaryExch"));
                    contracts.add(contract);
                    System.out.println("Contract description is loaded. Symbol: " + contract.symbol() +
                            ", secType: " + contract.secType() + ", currency: " + contract.currency() +
                            ", exchange: " + contract.exchange() +
                            ", primary exchange: " + contract.primaryExch());

//                    if (false) {
                    if (next.getBoolean("enabled")) {
                        System.out.println("Updating historical data for this contract...");
                        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
                        Document lastItem = hystoricalData.find(eq("contract", getContractId(contract)))
                                .sort(Sorts.descending("timestamp")).first();
                        if (lastItem != null) {
                            startOfPeriod.setTimeInMillis(lastItem.getLong("timestamp"));
                        } else {
                            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
                        }
                        Calendar endOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
                        endOfPeriod.add(Calendar.MINUTE, 0);
                        HistoryItem item = new HistoryItem(contract, "1 min", "MIDPOINT");
                        do {
                            while (requests.size()>40) {Thread.sleep(500);}
                            requestHistoryData(this.getClient(), item, startOfPeriod);
                            startOfPeriod.add(Calendar.MINUTE, 24 * 60);
                        } while (startOfPeriod.before(endOfPeriod));
                        // Waiting for the end of contract updating
                        while (!requests.isEmpty()) {
                            Thread.sleep(500);
                        }
                        System.out.println("The update of historical data is completed.");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                cursor.close();
                System.out.println("Loading collection of contracts is done");
            }

            // Load portfolio
            System.out.println("Loading portfolio of contracts...");
            cursor = database.getCollection("portfolio").find().iterator();
            try {
                while (cursor.hasNext()) {
                    Document next = cursor.next();
                    String contractId = next.getString("contract");
                    Document item = database.getCollection("contracts").find(eq("_id",contractId)).first();
                    if (item != null) {
                        String symbol = item.getString("symbol");
                        String currency = item.getString("currency");
                        String secType = item.getString("secType");
                        String exchange = item.getString("exchange");
                        for (Contract contract : contracts) {
                            if (contract.symbol().equals(symbol) &&
                                    contract.currency().equals(currency) &&
                                    contract.secType().getApiString().equals(secType) &&
                                    contract.exchange().equals(exchange)) {
                                portfolio.add(contract);
                                System.out.println("Portfolio item is loaded. Symbol: " + contract.symbol() +
                                        ", secType: " + contract.secType() + ", currency: " + contract.currency() +
                                        ", exchange: " + contract.exchange() +
                                        ", primary exchange: " + contract.primaryExch());
                                break;
                            }
                        }
                    }
                }
            } finally {
                cursor.close();
                System.out.println("Loading collection of contracts is done");
            }


            // Load portfolio
            System.out.println("Loading indicators...");
            cursor = database.getCollection("indicators").find().iterator();
            try {
                while (cursor.hasNext()) {
                    Document next = cursor.next();
                    String indicatorType = next.getString("indicatorType");
                    switch (indicatorType) {
                        case "MA":
                            Indicator indicator = MAIndicator.createFromDocument(next, hystoricalData);
                            System.out.println("Indicator " + indicator.getClassId() + " was loaded and will be updated");
                            indicator.update();
                            indicators.add(indicator);
                            break;
                        case "EMA":
                            indicator = EMAIndicator.createFromDocument(next, hystoricalData);
                            System.out.println("Indicator " + indicator.getClassId() + " was loaded and will be updated");
                            indicator.update();
                            indicators.add(indicator);
                            break;
                        case "MACD":
                            indicator = MACDIndicator.createFromDocument(next, hystoricalData);
                            System.out.println("Indicator " + indicator.getClassId() + " was loaded and will be updated");
                            indicator.update();
                            indicators.add(indicator);
                            break;
                        case "TrailedProfit":
                            indicator = TrailedProfitIndicator.createFromDocument(next, hystoricalData);
                            System.out.println("Indicator " + indicator.getClassId() + " was loaded and will be updated");
                            indicator.update();
                            indicators.add(indicator);
                            break;
                        default:
                            continue;
                    }
                }
            } catch (Exception e) {
                System.out.println("Unhandled exception: "+e.getLocalizedMessage());
                e.printStackTrace();
            } finally {
                cursor.close();
                System.out.println("Indicators loading is done");
            }


            //            MACDSignal signal = new MACDSignal(hystoricalData,new HistoryItem(portfolio.get(1), "1 min", "MIDPOINT"),17280, 37440, 12960);
//            signal.newTrain(
            FFNSignal signal = new FFNSignal(hystoricalData,new HistoryItem(portfolio.get(1), "1 min", "MIDPOINT"), 1000);
            signal.trainFFN(
                    new String[]{
                            "MSFT-USD-STK-SMART-1 min-MIDPOINT",
//                            "SBUX-USD-STK-SMART-1 min-MIDPOINT",
//                            "NXPI-USD-STK-SMART-1 min-MIDPOINT",
//                            "FB-USD-STK-SMART-1 min-MIDPOINT",
//                            "JNJ-USD-STK-SMART-1 min-MIDPOINT",
//                            "BRK B-USD-STK-SMART-1 min-MIDPOINT",
//                            "CNC-USD-STK-SMART-1 min-MIDPOINT",
//                            "AAPL-USD-STK-SMART-1 min-MIDPOINT",
//                            "SFM-USD-STK-SMART-1 min-MIDPOINT",
                            "DWDP-USD-STK-SMART-1 min-MIDPOINT"
                    },
                    new String[]{
                            "TrailedProfit-5-MSFT-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-SBUX-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-NXPI-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-FB-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-JNJ-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-BRK B-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-CNC-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-AAPL-USD-STK-SMART-1 min-MIDPOINT",
//                            "TrailedProfit-10-SFM-USD-STK-SMART-1 min-MIDPOINT",
                            "TrailedProfit-5-DWDP-USD-STK-SMART-1 min-MIDPOINT"
                    });


        } else {
            System.out.println("Contract collection is not initialized. Contracts cannot be loaded.");
        }

    }

    private void requestHistoryData(EClientSocket client, HistoryItem item, Calendar startDate) {
        SimpleDateFormat form = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        String formatted = form.format(startDate.getTime());
        int reqId = new Random().nextInt();
        this.requests.put(reqId, item);
        client.reqHistoricalData(reqId,
                item.contract, formatted, getDuration(item.barSize), item.barSize, item.dataType, 0, 1, false, null);
    }


//    @Override
//    public void tickPrice(int i, int i1, double v, TickAttrib tickAttr) {
//
//    }

    @Override
    public void tickPrice(int i, int i1, double v, TickAttr tickAttr) {

    }

    //! [ticksize]
    @Override
    public void tickSize(int tickerId, int field, int size) {
        System.out.println("Tick Size. Ticker Id:" + tickerId + ", Field: " + field + ", Size: " + size);
    }
    //! [ticksize]

    //! [tickoptioncomputation]
    @Override
    public void tickOptionComputation(int tickerId, int field,
                                      double impliedVol, double delta, double optPrice,
                                      double pvDividend, double gamma, double vega, double theta,
                                      double undPrice) {
        System.out.println("TickOptionComputation. TickerId: " + tickerId + ", field: " + field + ", ImpliedVolatility: " + impliedVol + ", Delta: " + delta
                + ", OptionPrice: " + optPrice + ", pvDividend: " + pvDividend + ", Gamma: " + gamma + ", Vega: " + vega + ", Theta: " + theta + ", UnderlyingPrice: " + undPrice);
    }
    //! [tickoptioncomputation]

    //! [tickgeneric]
    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        System.out.println("Tick Generic. Ticker Id:" + tickerId + ", Field: " + TickType.getField(tickType) + ", Value: " + value);
    }
    //! [tickgeneric]

    //! [tickstring]
    @Override
    public void tickString(int tickerId, int tickType, String value) {
        System.out.println("Tick string. Ticker Id:" + tickerId + ", Type: " + tickType + ", Value: " + value);
    }

    //! [tickstring]
    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints,
                        String formattedBasisPoints, double impliedFuture, int holdDays,
                        String futureLastTradeDate, double dividendImpact,
                        double dividendsToLastTradeDate) {
        System.out.println("TickEFP. " + tickerId + ", Type: " + tickType + ", BasisPoints: " + basisPoints + ", FormattedBasisPoints: " +
                formattedBasisPoints + ", ImpliedFuture: " + impliedFuture + ", HoldDays: " + holdDays + ", FutureLastTradeDate: " + futureLastTradeDate +
                ", DividendImpact: " + dividendImpact + ", DividendsToLastTradeDate: " + dividendsToLastTradeDate);
    }

    //! [orderstatus]
    @Override
    public void orderStatus(int orderId, String status, double filled,
                            double remaining, double avgFillPrice, int permId, int parentId,
                            double lastFillPrice, int clientId, String whyHeld, double v4) {
        System.out.println("OrderStatus. Id: " + orderId + ", Status: " + status + ", Filled" + filled + ", Remaining: " + remaining
                + ", AvgFillPrice: " + avgFillPrice + ", PermId: " + permId + ", ParentId: " + parentId + ", LastFillPrice: " + lastFillPrice +
                ", ClientId: " + clientId + ", WhyHeld: " + whyHeld);
    }
    //! [orderstatus]

    //! [openorder]
    @Override
    public void openOrder(int orderId, Contract contract, Order order,
                          OrderState orderState) {
        System.out.println("OpenOrder. ID: " + orderId + ", " + contract.symbol() + ", " + contract.secType() + " @ " + contract.exchange() + ": " +
                order.action() + ", " + order.orderType() + " " + order.totalQuantity() + ", " + orderState.status());
    }
    //! [openorder]

    //! [openorderend]
    @Override
    public void openOrderEnd() {
        System.out.println("OpenOrderEnd");
    }
    //! [openorderend]

    //! [updateaccountvalue]
    @Override
    public void updateAccountValue(String key, String value, String currency,
                                   String accountName) {
        System.out.println("UpdateAccountValue. Key: " + key + ", Value: " + value + ", Currency: " + currency + ", AccountName: " + accountName);
    }
    //! [updateaccountvalue]

    //! [updateportfolio]
    @Override
    public void updatePortfolio(Contract contract, double position,
                                double marketPrice, double marketValue, double averageCost,
                                double unrealizedPNL, double realizedPNL, String accountName) {
        System.out.println("UpdatePortfolio. " + contract.symbol() + ", " + contract.secType() + " @ " + contract.exchange()
                + ": Position: " + position + ", MarketPrice: " + marketPrice + ", MarketValue: " + marketValue + ", AverageCost: " + averageCost
                + ", UnrealisedPNL: " + unrealizedPNL + ", RealisedPNL: " + realizedPNL + ", AccountName: " + accountName);
    }
    //! [updateportfolio]

    //! [updateaccounttime]
    @Override
    public void updateAccountTime(String timeStamp) {
        System.out.println("UpdateAccountTime. Time: " + timeStamp + "\n");
    }
    //! [updateaccounttime]

    //! [accountdownloadend]
    @Override
    public void accountDownloadEnd(String accountName) {
        System.out.println("Account download finished: " + accountName + "\n");
    }
    //! [accountdownloadend]

    //! [nextvalidid]
    @Override
    public void nextValidId(int orderId) {
        System.out.println("Next Valid Id: [" + orderId + "]");
        currentOrderId = orderId;
    }
    //! [nextvalidid]

    //! [contractdetails]
    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        System.out.println("ContractDetails. ReqId: [" + reqId + "] - [" + contractDetails.contract().symbol() + "], [" + contractDetails.contract().secType() + "], ConId: [" + contractDetails.contract().conid() + "] @ [" + contractDetails.contract().exchange() + "]");
    }

    //! [contractdetails]
    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        System.out.println("bondContractDetails");
    }

    //! [contractdetailsend]
    @Override
    public void contractDetailsEnd(int reqId) {
        System.out.println("ContractDetailsEnd. " + reqId + "\n");
    }
    //! [contractdetailsend]

    //! [execdetails]
    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        System.out.println("ExecDetails. " + reqId + " - [" + contract.symbol() + "], [" + contract.secType() + "], [" + contract.currency() + "], [" + execution.execId() + "], [" + execution.orderId() + "], [" + execution.shares() + "]");
    }
    //! [execdetails]

    //! [execdetailsend]
    @Override
    public void execDetailsEnd(int reqId) {
        System.out.println("ExecDetailsEnd. " + reqId + "\n");
    }
    //! [execdetailsend]

    //! [updatemktdepth]
    @Override
    public void updateMktDepth(int tickerId, int position, int operation,
                               int side, double price, int size) {
        System.out.println("UpdateMarketDepth. " + tickerId + " - Position: " + position + ", Operation: " + operation + ", Side: " + side + ", Price: " + price + ", Size: " + size + "");
    }

    @Override
    public void updateMktDepthL2(int i, int i1, String s, int i2, int i3, double v, int i4) {

    }

//    @Override
//    public void updateMktDepthL2(int i, int i1, String s, int i2, int i3, double v, int i4, boolean b) {
//
//    }

    //	//! [updatemktdepth]
//	@Override
//	public void updateMktDepthL2(int tickerId, int position,
//			String marketMaker, int operation, int side, double price, int size) {
//		System.out.println("updateMktDepthL2");
//	}
    //! [updatenewsbulletin]
    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message,
                                   String origExchange) {
        System.out.println("News Bulletins. " + msgId + " - Type: " + msgType + ", Message: " + message + ", Exchange of Origin: " + origExchange + "\n");
    }
    //! [updatenewsbulletin]

    //! [managedaccounts]
    @Override
    public void managedAccounts(String accountsList) {
        System.out.println("Account list: " + accountsList);
    }
    //! [managedaccounts]

    //! [receivefa]
    @Override
    public void receiveFA(int faDataType, String xml) {
        System.out.println("Receing FA: " + faDataType + " - " + xml);
    }

    //! [receivefa]

    //! [historicaldata]
    @Override
    public void historicalData(int reqId, Bar bar) {
        SimpleDateFormat ft = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        Calendar parsingDate = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        HistoryItem item = requests.get(reqId);
        item.setData(bar);
        hystoricalData.replaceOne(eq("_id", item.getId()), item.getDocument(), new ReplaceOptions().upsert(true));
        System.out.println("Update historical data item. Symbol: " + item.contract.symbol() + ", date: " + bar.time());
    }
    //! [historicaldata]



    @Override
    public void historicalDataEnd(int i, String startDate, String endDate) {
        requests.remove(i);
    }

    //! [scannerparameters]
    @Override
    public void scannerParameters(String xml) {
        System.out.println("ScannerParameters. " + xml + "\n");
    }
    //! [scannerparameters]

    //! [scannerdata]
    @Override
    public void scannerData(int reqId, int rank,
                            ContractDetails contractDetails, String distance, String benchmark,
                            String projection, String legsStr) {
        System.out.println("ScannerData. " + reqId + " - Rank: " + rank + ", Symbol: " + contractDetails.contract().symbol() + ", SecType: " + contractDetails.contract().secType() + ", Currency: " + contractDetails.contract().currency()
                + ", Distance: " + distance + ", Benchmark: " + benchmark + ", Projection: " + projection + ", Legs String: " + legsStr);
    }
    //! [scannerdata]

    //! [scannerdataend]
    @Override
    public void scannerDataEnd(int reqId) {
        System.out.println("ScannerDataEnd. " + reqId);
    }
    //! [scannerdataend]

    //! [realtimebar]
    @Override
    public void realtimeBar(int reqId, long time, double open, double high,
                            double low, double close, long volume, double wap, int count) {
        System.out.println("RealTimeBars. " + reqId + " - Time: " + time + ", Open: " + open + ", High: " + high + ", Low: " + low + ", Close: " + close + ", Volume: " + volume + ", Count: " + count + ", WAP: " + wap);
    }

    //! [realtimebar]
    @Override
    public void currentTime(long time) {
        System.out.println("currentTime");
    }

    //! [fundamentaldata]
    @Override
    public void fundamentalData(int reqId, String data) {
        System.out.println("FundamentalData. ReqId: [" + reqId + "] - Data: [" + data + "]");
    }

    //! [fundamentaldata]
    @Override
    public void deltaNeutralValidation(int reqId, DeltaNeutralContract underComp) {
        System.out.println("deltaNeutralValidation");
    }

    //! [ticksnapshotend]
    @Override
    public void tickSnapshotEnd(int reqId) {
        System.out.println("TickSnapshotEnd: " + reqId);
    }
    //! [ticksnapshotend]

    //! [marketdatatype]
    @Override
    public void marketDataType(int reqId, int marketDataType) {
        System.out.println("MarketDataType. [" + reqId + "], Type: [" + marketDataType + "]\n");
    }
    //! [marketdatatype]

    //! [commissionreport]
    @Override
    public void commissionReport(CommissionReport commissionReport) {
        System.out.println("CommissionReport. [" + commissionReport.m_execId + "] - [" + commissionReport.m_commission + "] [" + commissionReport.m_currency + "] RPNL [" + commissionReport.m_realizedPNL + "]");
    }
    //! [commissionreport]

    //! [position]
    @Override
    public void position(String account, Contract contract, double pos,
                         double avgCost) {
        System.out.println("Position. " + account + " - Symbol: " + contract.symbol() + ", SecType: " + contract.secType() + ", Currency: " + contract.currency() + ", Position: " + pos + ", Avg cost: " + avgCost);
    }
    //! [position]

    //! [positionend]
    @Override
    public void positionEnd() {
        System.out.println("PositionEnd \n");
    }
    //! [positionend]

    //! [accountsummary]
    @Override
    public void accountSummary(int reqId, String account, String tag,
                               String value, String currency) {
        System.out.println("Acct Summary. ReqId: " + reqId + ", Acct: " + account + ", Tag: " + tag + ", Value: " + value + ", Currency: " + currency);
    }
    //! [accountsummary]

    //! [accountsummaryend]
    @Override
    public void accountSummaryEnd(int reqId) {
        System.out.println("AccountSummaryEnd. Req Id: " + reqId + "\n");
    }

    //! [accountsummaryend]
    @Override
    public void verifyMessageAPI(String apiData) {
        System.out.println("verifyMessageAPI");
    }

    @Override
    public void verifyCompleted(boolean isSuccessful, String errorText) {
        System.out.println("verifyCompleted");
    }

    @Override
    public void verifyAndAuthMessageAPI(String apiData, String xyzChallange) {
        System.out.println("verifyAndAuthMessageAPI");
    }

    @Override
    public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {
        System.out.println("verifyAndAuthCompleted");
    }

    //! [displaygrouplist]
    @Override
    public void displayGroupList(int reqId, String groups) {
        System.out.println("Display Group List. ReqId: " + reqId + ", Groups: " + groups + "\n");
    }
    //! [displaygrouplist]

    //! [displaygroupupdated]
    @Override
    public void displayGroupUpdated(int reqId, String contractInfo) {
        System.out.println("Display Group Updated. ReqId: " + reqId + ", Contract info: " + contractInfo + "\n");
    }

    //! [displaygroupupdated]
    @Override
    public void error(Exception e) {
        System.out.println("Exception: " + e.getMessage());
    }

    @Override
    public void error(String str) {
        System.out.println("Error STR");
    }

    //! [error]
    @Override
    public void error(int id, int errorCode, String errorMsg) {
        System.out.println("Error. Id: " + id + ", Code: " + errorCode + ", Msg: " + errorMsg + "\n");
    }

    //! [error]
    @Override
    public void connectionClosed() {
        System.out.println("Connection closed");
    }

    //! [connectack]
    @Override
    public void connectAck() {
        if (clientSocket.isAsyncEConnect()) {
            System.out.println("Acknowledging connection");
            clientSocket.startAPI();
        }
    }
    //! [connectack]

    //! [positionmulti]
    @Override
    public void positionMulti(int reqId, String account, String modelCode,
                              Contract contract, double pos, double avgCost) {
        System.out.println("Position Multi. Request: " + reqId + ", Account: " + account + ", ModelCode: " + modelCode + ", Symbol: " + contract.symbol() + ", SecType: " + contract.secType() + ", Currency: " + contract.currency() + ", Position: " + pos + ", Avg cost: " + avgCost + "\n");
    }
    //! [positionmulti]

    //! [positionmultiend]
    @Override
    public void positionMultiEnd(int reqId) {
        System.out.println("Position Multi End. Request: " + reqId + "\n");
    }
    //! [positionmultiend]

    //! [accountupdatemulti]
    @Override
    public void accountUpdateMulti(int reqId, String account, String modelCode,
                                   String key, String value, String currency) {
        System.out.println("Account Update Multi. Request: " + reqId + ", Account: " + account + ", ModelCode: " + modelCode + ", Key: " + key + ", Value: " + value + ", Currency: " + currency + "\n");
    }
    //! [accountupdatemulti]

    //! [accountupdatemultiend]
    @Override
    public void accountUpdateMultiEnd(int reqId) {
        System.out.println("Account Update Multi End. Request: " + reqId + "\n");
    }
    //! [accountupdatemultiend]

    //! [securityDefinitionOptionParameter]
    @Override
    public void securityDefinitionOptionalParameter(int reqId, String exchange,
                                                    int underlyingConId, String tradingClass, String multiplier,
                                                    Set<String> expirations, Set<Double> strikes) {
        System.out.println("Security Definition Optional Parameter. Request: " + reqId + ", Trading Class: " + tradingClass + ", Multiplier: " + multiplier + " \n");
    }

    //! [securityDefinitionOptionParameter]
    @Override
    public void securityDefinitionOptionalParameterEnd(int reqId) {
        // TODO Auto-generated method stub

    }

    @Override
    public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {
        for (SoftDollarTier tier : tiers) {
            System.out.print("tier: " + tier + ", ");
        }

        System.out.println();
    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {

    }

    @Override
    public void symbolSamples(int i, ContractDescription[] contractDescriptions) {

    }


    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {

    }

    @Override
    public void tickNews(int i, long l, String s, String s1, String s2, String s3) {

    }

    @Override
    public void smartComponents(int i, Map<Integer, Map.Entry<String, Character>> map) {

    }

    @Override
    public void tickReqParams(int i, double v, String s, int i1) {

    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {

    }

    @Override
    public void newsArticle(int i, int i1, String s) {

    }

    @Override
    public void historicalNews(int i, String s, String s1, String s2, String s3) {

    }

    @Override
    public void historicalNewsEnd(int i, boolean b) {

    }

    @Override
    public void headTimestamp(int i, String s) {

    }

    @Override
    public void histogramData(int i, List<HistogramEntry> list) {

    }

    @Override
    public void historicalDataUpdate(int i, Bar bar) {

    }

    @Override
    public void rerouteMktDataReq(int i, int i1, String s) {

    }

    @Override
    public void rerouteMktDepthReq(int i, int i1, String s) {

    }

    @Override
    public void marketRule(int i, PriceIncrement[] priceIncrements) {

    }

    @Override
    public void pnl(int i, double v, double v1, double v2) {

    }

    @Override
    public void pnlSingle(int i, int i1, double v, double v1, double v2, double v3) {

    }

    @Override
    public void historicalTicks(int i, List<HistoricalTick> list, boolean b) {

    }

    @Override
    public void historicalTicksBidAsk(int i, List<HistoricalTickBidAsk> list, boolean b) {

    }

    @Override
    public void historicalTicksLast(int i, List<HistoricalTickLast> list, boolean b) {

    }

    @Override
    public void tickByTickAllLast(int i, int i1, long l, double v, int i2, TickAttr tickAttr, String s, String s1) {

    }

    @Override
    public void tickByTickBidAsk(int i, long l, double v, double v1, int i1, int i2, TickAttr tickAttr) {

    }

//    @Override
//    public void tickByTickAllLast(int i, int i1, long l, double v, int i2, TickAttribLast tickAttribLast, String s, String s1) {
//
//    }
//
//    @Override
//    public void tickByTickBidAsk(int i, long l, double v, double v1, int i1, int i2, TickAttribBidAsk tickAttribBidAsk) {
//
//    }

//	@Override
//	public void tickByTickAllLast(int i, int i1, long l, double v, int i2, TickAttrib tickAttr, String s, String s1) {
//
//	}

//	@Override
//	public void tickByTickBidAsk(int i, long l, double v, double v1, int i1, int i2, TickAttrib tickAttr) {
//
//	}

    @Override
    public void tickByTickMidPoint(int i, long l, double v) {

    }

//    @Override
//    public void orderBound(long l, int i, int i1) {
//
//    }

    private String getDuration(String barSize) {
        switch (barSize) {
            case "1 day":
                return "1 Y";
            case "1 hour":
                return "1 M";
            case "2 hours":
                return "1 M";
            case "3 hours":
                return "1 M";
            case "4 hours":
                return "1 M";
            case "8 hours":
                return "1 M";
            case "1 min":
                return "1 D";
            case "2 mins":
                return "2 D";
            case "3 mins":
                return "1 W";
            case "5 mins":
                return "1 W";
            case "10 mins":
                return "1 W";
            case "15 mins":
                return "1 W";
            case "20 mins":
                return "1 W";
            case "30 mins":
                return "1 M";
            case "1 secs":
                return "1800 S";
            case "5 secs":
                return "3600 S";
            case "10 secs":
                return "14400 S";
            case "15 secs":
                return "14400 S";
            case "30 secs":
                return "28800 S";
        }
        return null;
    }

    private static String getContractId(Contract contract) {
        return contract.symbol()+"-"+contract.currency()+"-"+contract.secType().getApiString()+"-"+contract.exchange();
    }

    private Contract findContract(String id) {
        if (contracts != null && !contracts.isEmpty()) {
            for (Contract contract : contracts) {
                if (getContractId((contract)).equals(id)) {
                    return contract;
                }
            }
        }
        return null;
    }

}
