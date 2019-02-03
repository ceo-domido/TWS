import com.ib.client.Contract;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import static com.mongodb.client.model.Filters.*;

public class HistoryData extends Indicator {
    public Contract contract;
    public String barSize;
    public String dataType;


    public HistoryData(MongoCollection<Document> history, Contract contract, String barSize, String dataType) {
        this.historyData = history;
        this.contract = contract;
        this.barSize = barSize;
        this.dataType = dataType;
    }

    protected DateItem getData(long timestamp) {
        Document doc;
        if (timestamp != 0) {
            doc = historyData.find(and(eq("class", getClassId())
                    , lte("timestamp", timestamp))).sort(Sorts.descending("timestamp")).first();
        } else {
            doc = historyData.find(eq("class", getClassId()))
                    .sort(Sorts.descending("timestamp")).first();
        }
        return HistoryItem.createFromDocument(doc);
    }


    @Override
    public double getValue(String field, long timestamp) {
        return 0;
    }

    @Override
    public String getClassId() {
        return contract.symbol()+"-"+contract.currency()+"-"+contract.secType()+"-"+contract.exchange() +"-"+barSize+"-"+dataType;
    }

//    @Override
    public void update() {
//        System.out.println("Updating values for history data...");
//        // start is now
//        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
//        // initialize current value
//        HistoryItem item = new HistoryItem(contract,barSize,dataType);
//        // find last EMA record
//        Document lastValue = historyData.find(eq("class",item.getClassId()))
//                .sort(Sorts.descending("timestamp")).first();
//        // if last record exist then start from it else start from some months later
//        if (lastValue != null) {
//            startOfPeriod.setTimeInMillis(lastValue.getLong("timestamp"));
//            item = HistoryItem.createFromDocument(lastValue);
//        } else {
//            // compute start value
//            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
//        }
//        Calendar endOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
//        endOfPeriod.add(Calendar.MINUTE, 0);
//        HistoryItem item = new HistoryItem(contract, "1 min", "MIDPOINT");
//        do {
//            requestHistoryData(this.getClient(), item, startOfPeriod);
//            startOfPeriod.add(Calendar.MINUTE, 24 * 60);
//        } while (startOfPeriod.before(endOfPeriod));
//        // Waiting for the end of contract updating
//        while (!requests.isEmpty()) {
//            Thread.sleep(500);
//        }
//        System.out.println("The update of historical data is completed.");
//    }
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
        doc.append("indicatorType", "EMA");
        doc.append("class",getClassId());
        return doc;
    }

    public static HistoryData createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        int period = doc.getInteger("period");
        return new HistoryData(history,item.contract, item.barSize,item.dataType);
    }

    public String getId() {
        return "EMA-"+"-"+historyItem.getContractId();
    }
}
