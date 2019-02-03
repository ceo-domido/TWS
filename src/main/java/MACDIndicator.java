import com.ib.client.Contract;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.Calendar;
import java.util.TimeZone;

import static com.mongodb.client.model.Filters.*;

public class MACDIndicator extends Indicator {
    private int sPeriod;
    private int lPeriod;
    private int aPeriod;
    public EMAIndicator shortEMA;
    public EMAIndicator longEMA;

    public MACDIndicator(MongoCollection<Document> history, HistoryItem historyItem, int shortPeriod, int longPeriod, int averagePeriod) {
        super();
        this.historyData = history;
        this.historyItem = historyItem;
        this.sPeriod = shortPeriod;
        this.lPeriod = longPeriod;
        this.aPeriod = averagePeriod;
        shortEMA = new EMAIndicator(history,historyItem,shortPeriod);
        longEMA = new EMAIndicator(history,historyItem,longPeriod);
    }

    @Override
    public double getValue(String field, long timestamp) {
        return 0;
    }

    @Override
    public String getClassId() {
        return "MACD-"+String.valueOf(sPeriod)+"-"+String.valueOf(lPeriod)+"-"+String.valueOf(aPeriod)+"-"+historyItem.getContractId();
    }

    public String getId() {
        return getClassId();
    }

    @Override
    public void update() {
        System.out.println("Updating values for MACD indicator...");
        shortEMA.update();
        longEMA.update();
        //Determine start time for updating
        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        MACDItem macdItem = new MACDItem(this.getClassId(),this.aPeriod);
        Document lastValue = historyData.find(eq("class",macdItem.getClassId()))
                .sort(Sorts.descending("timestamp")).first();
        if (lastValue != null) {
            startOfPeriod.setTimeInMillis(lastValue.getLong("timestamp"));
        } else {
            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
        }

        MongoCursor<Document> cursor = historyData
                .find(and(eq("class",historyItem.getClassId()),gt("timestamp",startOfPeriod.getTimeInMillis())))
                .sort(Sorts.ascending("timestamp")).iterator();
        while (cursor.hasNext()) {
            long time = cursor.next().getLong("timestamp");
            Document sEMA = historyData.find(eq("_id",shortEMA.getClassId()+"-"+String.valueOf(time))).first();
            Document lEMA = historyData.find(eq("_id",longEMA.getClassId()+"-"+String.valueOf(time))).first();
            if (sEMA != null && lEMA != null) {
                EMAItem sItem = EMAItem.createFromDocument(sEMA);
                EMAItem lItem = EMAItem.createFromDocument(lEMA);
                macdItem.sum(sItem.sub(lItem));
                macdItem.timestamp = time;
                historyData.insertOne(macdItem.getDocument());
                System.out.println("MACD value updated for date "+ macdItem.timestamp);
            }
        }
    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getId());
        doc.append("barSize", historyItem.barSize);
        doc.append("dataType", historyItem.dataType);
        doc.append("exchange", historyItem.contract.exchange());
        doc.append("secType", historyItem.contract.secType());
        doc.append("currency", historyItem.contract.currency());
        doc.append("symbol", historyItem.contract.symbol());
        doc.append("shortPeriod", sPeriod);
        doc.append("longPeriod", lPeriod);
        doc.append("averagePeriod", aPeriod);
        doc.append("indicatorType", "MACD");
        doc.append("class", getClassId());
        return doc;
    }

    public static MACDIndicator createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        int sPeriod = doc.getInteger("shortPeriod");
        int lPeriod = doc.getInteger("longPeriod");
        int aPeriod = doc.getInteger("averagePeriod");
        return new MACDIndicator(history,item,sPeriod,lPeriod,aPeriod);
    }

}
