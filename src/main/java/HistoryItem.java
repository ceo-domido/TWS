import com.ib.client.Bar;
import com.ib.client.Contract;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
public class HistoryItem implements DateItem {

    public HistoryItem() {
        contract = new Contract();
        barSize = "1 min";
        dataType = "MIDPOINT";
        id = new ObjectId();
    }

    public HistoryItem(Contract contract, String barSize, String dataType) {
        this.contract = contract;
        this.barSize = barSize;
        this.dataType = dataType;
        this.id = new ObjectId();
    }
    
    public Contract contract;
    public String barSize;
    public String dataType;
    public long timestamp;
    protected Bar data = null;

    public Bar getData() {
        return data;
    }

    public void setData(Bar data) {
        SimpleDateFormat ft = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        Calendar parsingDate = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        this.data = data;
        try {
            parsingDate.setTime(ft.parse(data.time()));
            timestamp = parsingDate.getTimeInMillis();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public ObjectId id;

    static public HistoryItem createFromDocument(Document doc) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        item.timestamp = doc.getLong("timestamp");
        item.data = new Bar(
                doc.getString("time"),
                doc.getDouble("open"),
                doc.getDouble("high"),
                doc.getDouble("low"),
                doc.getDouble("close"),
                doc.getLong("volume"),
                doc.getInteger("count"),
                doc.getDouble("wap")
        );
        return item;
    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getId());
        doc.append("barSize", barSize);
        doc.append("dataType", dataType);
        doc.append("exchange", contract.exchange());
        doc.append("secType", contract.secType().getApiString());
        doc.append("currency", contract.currency());
        doc.append("symbol", contract.symbol());
        doc.append("timestamp", timestamp);
        doc.append("time", data.time());
        doc.append("open", data.open());
        doc.append("high", data.high());
        doc.append("low", data.close());
        doc.append("close",data.close());
        doc.append("volume", data.volume());
        doc.append("count", data.count());
        doc.append("wap",data.wap());
        doc.append("class",getClassId());
        doc.append("contract", getContractId());
        return doc;
    }

    public String getContractId() {
        String result = contract.symbol()+"-"+contract.currency()+"-"+contract.secType()+"-"+contract.exchange();
        return  result;
    }

    public String getClassId() {
        String result = contract.symbol()+"-"+contract.currency()+"-"+contract.secType()+"-"+contract.exchange() +"-"+barSize+"-"+dataType;
        return  result;
    }

    public String getId() {
        return getClassId() + "-" + String.valueOf(timestamp);
    }
}
