//Abstraction is the process of hiding implementation details and exposing only the essential behavior of an object.
abstract class PaymentMethod {
    public abstract void pay(double amount);
    public void generateReceipt() {
        System.out.println("Receipt generated");
    }
}
class UpiPayment extends PaymentMethod {
    @Override
    public void pay(double amount) {
        System.out.println("Processing UPI payment of " + amount);
    }
}
class CardPayment extends PaymentMethod {
    @Override
    public void pay(double amount) {
        System.out.println("Processing Card payment of " + amount);
    }
}
public class Main {
    public static void main(String[] args) {
        PaymentMethod upiPayment = new UpiPayment();
        PaymentMethod cardPayment = new CardPayment();
        upiPayment.pay(500);
        upiPayment.generateReceipt();
        cardPayment.pay(1000);
        cardPayment.generateReceipt();
    }
}
