import com.ib.client.Contract;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

public class SimpleNNSignal extends Indicator{
    private int window;
    private HistoryItem historyItem;

    public SimpleNNSignal(MongoCollection<Document> history, HistoryItem historyItem, int window) {
        this.historyData = history;
        this.window = window;
        this.historyItem = historyItem;
    }


    @Override
    public double getValue(String field, long timestamp) {
        return 0;
    }

    @Override
    public String getClassId() {
        return "SimpleNN-"+String.valueOf(window)+"-"+historyItem.getClassId();
    }

    @Override
    public void update() {

    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getClassId());
        doc.append("barSize", historyItem.barSize);
        doc.append("dataType", historyItem.dataType);
        doc.append("exchange", historyItem.contract.exchange());
        doc.append("secType", historyItem.contract.secType());
        doc.append("currency", historyItem.contract.currency());
        doc.append("symbol", historyItem.contract.symbol());
        doc.append("window", window);
        doc.append("indicatorType", "SimpleNN");
        doc.append("class",getClassId());
        return doc;
    }

    public static SimpleNNSignal createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        int window = doc.getInteger("window");
        return new SimpleNNSignal(history, item, window);
    }

    public void train() {

    }

}
