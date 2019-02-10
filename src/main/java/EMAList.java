import java.util.Stack;

public class EMAList<T extends Number> extends Stack<Number> {
    private Double a;

    public EMAList(int period) {
        this.a = 2.0/(1+period);
    }

    @Override
    public T push(Number newNumber) {
        Double newValue = (peek().doubleValue()*(1-a.doubleValue())+newNumber.doubleValue()*a);
        return (T) super.push(newValue);
    }
}
