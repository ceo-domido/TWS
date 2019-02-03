import com.ib.client.Contract;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.Calendar;
import java.util.TimeZone;

import static com.mongodb.client.model.Filters.*;

public class EMAIndicator extends MAIndicator {

    public EMAIndicator(MongoCollection<Document> history, HistoryItem historyItem, int period) {
        super(history, historyItem, period);
    }

    @Override
    public String getClassId() {
        return "EMA-"+String.valueOf(period)+"-"+historyItem.getClassId();
    }



    @Override
    public void update() {
        System.out.println("Updating values for EMA indicator...");
        // start is now
        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        // initialize current value
        EMAItem emaItem = new EMAItem(this.getClassId(),period);
        // find last EMA record
        Document lastValue = historyData.find(eq("class",emaItem.getClassId()))
                .sort(Sorts.descending("timestamp")).first();
        // if last record exist then start from it else start from some months later
        if (lastValue != null) {
            startOfPeriod.setTimeInMillis(lastValue.getLong("timestamp"));
            emaItem = EMAItem.createFromDocument(lastValue);
        } else {
            // compute start value
            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
            // find some history items before start time
            MongoCursor<Document> cursor = historyData
                    .find(and(eq("class", historyItem.getClassId()), lte("timestamp", startOfPeriod.getTimeInMillis())))
                    .sort(Sorts.descending("timestamp")).limit(period)
                    .sort(Sorts.ascending("timestamp")).iterator();
            // sum all found values
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                HistoryItem item = HistoryItem.createFromDocument(doc);
                emaItem.sum(item.data);
            }
        }
        MongoCursor<Document> cursor = historyData
                .find(and(eq("class",historyItem.getClassId()),gt("timestamp",startOfPeriod.getTimeInMillis())))
                .sort(Sorts.ascending("timestamp")).iterator();
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            HistoryItem newItem = HistoryItem.createFromDocument(doc);
            emaItem.sum(newItem.data);
            emaItem.timestamp = newItem.timestamp;
            historyData.insertOne(emaItem.getDocument());
            System.out.println("EMA value updated for date "+newItem.data.time());
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
        doc.append("period", period);
        doc.append("indicatorType", "EMA");
        doc.append("class",getClassId());
        return doc;
    }

    public static EMAIndicator createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        int period = doc.getInteger("period");
        return new EMAIndicator(history,item,period);
    }

    public String getId() {
        return getClassId();
    }
}
