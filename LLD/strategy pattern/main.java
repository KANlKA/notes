//Imagine an application that supports multiple payment methods: credit card, upi., emi etc
//BAD APPROACH
class PaymentService {
    public void pay(String type, double amount) {
        if (type.equals("UPI")) {
            System.out.println("Processing UPI payment");
        } 
        else if (type.equals("CARD")) {
            System.out.println("Processing Card payment");
        } 
        else if (type.equals("PAYPAL")) {
            System.out.println("Processing PayPal payment");
        }
    }
}
//WHATS WRONG??
//Every time we add a payment method, we modify existing code. This violates the Open/Closed Principle.
interface PaymentStrategy {
    void pay(double amount);
}
class UpiPayment implements PaymentStrategy {
    public void pay(double amount) {
        System.out.println("Paid using UPI: " + amount);
    }
}
class CardPayment implements PaymentStrategy {
    public void pay(double amount) {
        System.out.println("Paid using Card: " + amount);
    }
}
class PaymentService {
    private PaymentStrategy paymentStrategy;
    public PaymentService(PaymentStrategy paymentStrategy) {
        this.paymentStrategy = paymentStrategy;
    }
    public void processPayment(double amount) {
        paymentStrategy.pay(amount);
    }
}
