import com.ib.client.Contract;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

public class MAIndicator extends Indicator {
    protected int period;

    public MAIndicator(MongoCollection<Document> history, HistoryItem historyItem, int period) {
        this.historyData = history;
        this.historyItem = historyItem;
        this.period = period;
    }

    @Override
    public void update() {
        System.out.println("Updating values for MA indicator...");
        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        Document lastValue = historyData.find(eq("class",getClassId()))
                .sort(Sorts.descending("timestamp")).first();
        if (lastValue != null) {
            startOfPeriod.setTimeInMillis(lastValue.getLong("timestamp"));
        } else {
            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
        }
        Deque<HistoryItem> window = new ArrayDeque(period);
        MongoCursor<Document> cursor = historyData
                .find(and(eq("contract",historyItem.getContractId()),lte("timestamp",startOfPeriod.getTimeInMillis())))
                .sort(Sorts.descending("timestamp")).limit(period).iterator();
        HistoryItem lastItem = null;
        while (cursor.hasNext()) {
            lastItem = HistoryItem.createFromDocument(cursor.next());
            window.offerLast(lastItem);
        }
        while (window.size()<period) {
            window.offerLast(lastItem);
        }
        MAItem maItem = new MAItem(historyItem.getContractId(),period);
        for (HistoryItem item : window) {
            maItem.add(item.data);
        }
        maItem.timestamp = startOfPeriod.getTimeInMillis();
        cursor = historyData
                .find(and(eq("contract",historyItem.getContractId()),gt("timestamp",startOfPeriod.getTimeInMillis())))
                .sort(Sorts.ascending("timestamp")).iterator();
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            HistoryItem newItem = HistoryItem.createFromDocument(doc);
            window.offerFirst(newItem);
            lastItem = window.peekLast();
            maItem.add(newItem.data).sub(lastItem.data);
            maItem.timestamp = newItem.timestamp;
            historyData.insertOne(maItem.getDocument());
        }
    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getClassId());
        doc.append("class", getClassId());
        doc.append("barSize", historyItem.barSize);
        doc.append("dataType", historyItem.dataType);
        doc.append("exchange", historyItem.contract.exchange());
        doc.append("secType", historyItem.contract.secType());
        doc.append("currency", historyItem.contract.currency());
        doc.append("symbol", historyItem.contract.symbol());
        doc.append("period", period);
        doc.append("indicatorType", "MA");
        return doc;
    }

    public static MAIndicator createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        int period = doc.getInteger("period");
        return new MAIndicator(history,item,period);
    }

    @Override
    public double getValue(String field, long timestamp) {
        MAItem item = (MAItem) getData(timestamp);
        switch (field) {
            case "open": return item.maOpen;
            case "close": return item.maClose;
            case "high": return item.maHigh;
            case "low": return item.maLow;
            case "count": return item.maCount;
            case "wap": return  item.maWAP;
            case "volume": return item.maVolume;
            default: return 0;
        }
    }

    protected DateItem getData(long timestamp)
    {
        Document doc;
        if (timestamp != 0) {
            doc = historyData.find(and(eq("class", getClassId())
                    , lte("timestamp", timestamp))).sort(Sorts.descending("timestamp")).first();
        } else {
            doc = historyData.find(eq("class", getClassId()))
                    .sort(Sorts.descending("timestamp")).first();
        }
        return MAItem.createFromDocument(doc);
    }

    public String getClassId() {
        return "MA-"+String.valueOf(period)+"-"+historyItem.getClassId();
    }

}
