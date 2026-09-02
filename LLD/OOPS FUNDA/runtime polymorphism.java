//Polymorphism means "many forms." It allows objects of different classes to be treated through a common interface while executing their own specific implementation of a behavior.
interface PaymentMethod {
    void pay(double amount);
}
class UpiPayment implements PaymentMethod {
    @Override
    public void pay(double amount) {
        System.out.println("Paid " + amount + " using UPI");
    }
}
class CardPayment implements PaymentMethod {
    @Override
    public void pay(double amount) {
        System.out.println("Paid " + amount + " using Credit Card");
    }
}
class PaypalPayment implements PaymentMethod {
    @Override
    public void pay(double amount) {
        System.out.println("Paid " + amount + " using PayPal");
    }
}
class PaymentService {
    public void processPayment(PaymentMethod paymentMethod, double amount) {
        paymentMethod.pay(amount);
    }
}
public class Main {
    public static void main(String[] args) {
        PaymentService paymentService = new PaymentService();
        PaymentMethod payment1 = new UpiPayment();
        PaymentMethod payment2 = new CardPayment();
        PaymentMethod payment3 = new PaypalPayment();
        paymentService.processPayment(payment1, 500);
        paymentService.processPayment(payment2, 1000);
        paymentService.processPayment(payment3, 2000);
    }
}
