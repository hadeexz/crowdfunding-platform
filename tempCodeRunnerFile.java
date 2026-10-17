
import java.util.ArrayList;

public class stack {

    public class stacka {
        ArrayList<Integer> list = new ArrayList<>();

        public boolean isEmpty() {
            return list.size() == 0;
        }

        public void push(int data) {
            list.add(data);
        }

        public int pop() {
            int top = list.get(list.size() - 1);
            list.remove(list.size() - 1);
            return top;
        }

        public int peak() {
            int top = list.get(list.size() - 1);
            return top;
        }
    }

    public static void main(String[] args) {

        stack obj = new stack();
        stack.stacka stk = obj.new stacka();

        stk.push(1);
        stk.push(3);
        stk.push(5);

        while (!stk.isEmpty()) {
            System.out.println(stk.pop());
        }
    }
}
