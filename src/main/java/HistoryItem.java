import com.ib.client.Bar;
import com.ib.client.Contract;
import com.mongodb.BasicDBObject;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author alexe
 */
public class HistoryItem implements MongoWrapper<HistoryItem> {

    public HistoryItem() {
        contract = new Contract();
        barSize = "1 min";
        dataType = "MIDPOINT";
        historyData = new TreeMap();
        id = new ObjectId();
    }

    public HistoryItem(Contract contract, String barSize, String dataType) {
        this.contract = contract;
        this.barSize = barSize;
        this.dataType = dataType;
        this.historyData = new TreeMap<>();
        this.id = new ObjectId();
    }
    
    public Contract contract;
    public String barSize;
    public String dataType;
    public TreeMap<Calendar, Bar> historyData;
    public ObjectId id;

    static public HistoryItem createFromDocument(Document doc) {
        HistoryItem item = new HistoryItem();
        item.id = doc.getObjectId("_id");
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        Contract contract = new Contract();
        contract.exchange(doc.getString("exchange"));
        contract.secType(doc.getString("secType"));
        contract.currency(doc.getString("currency"));
        contract.symbol(doc.getString("symbol"));
        List<Document> list = (List<Document>) doc.get("history", List.class);
        for (Document d : list) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(d.getLong("timestamp"));
            Bar bar = new Bar(
                    d.getString("time"),
                    d.getDouble("open"),
                    d.getDouble("high"),
                    d.getDouble("low"),
                    d.getDouble("close"),
                    d.getLong("volume"),
                    d.getInteger("count"),
                    d.getInteger("wap")
            );
            item.historyData.put(cal,bar);
        }
        return item;
    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", id);
        doc.append("barSize", barSize);
        doc.append("dataType", dataType);
        doc.append("exchange", contract.exchange());
        doc.append("secType", contract.secType());
        doc.append("currency", contract.currency());
        doc.append("symbol", contract.symbol());
        List<Document> list = new ArrayList();
        for (Map.Entry<Calendar,Bar> entry : historyData.entrySet()) {
            Document d = new Document();
            d.append("timestamp", entry.getKey().getTimeInMillis());
            d.append("time", entry.getValue().time());
            d.append("open", entry.getValue().open());
            d.append("high", entry.getValue().high());
            d.append("low", entry.getValue().close());
            d.append("close",entry.getValue().close());
            d.append("volume", entry.getValue().volume());
            d.append("count", entry.getValue().count());
            d.append("wap",entry.getValue().wap());
            list.add(d);
        }
        return doc;
    }

    public String getContractId() {
        return contract.symbol()+"-"+contract.currency()+"-"+contract.secType()+"-"+contract.exchange();
    }

}
