package extension;

/**
 * @author cyx
 * @create 2021-03-30 19:27
 */
public class Holder<T> {

    private volatile T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

}
