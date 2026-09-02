//Inheritance allows one class to acquire the properties and behavior of another class.
//IS A RELATIONSHIP
class Vehicle {
    protected String brand;
    protected String registrationNumber;
    public Vehicle(String brand, String registrationNumber) {
        this.brand = brand;
        this.registrationNumber = registrationNumber;
    }
    public void start() {
        System.out.println("Vehicle started");
    }
}
class Car extends Vehicle {
    public Car(String brand, String registrationNumber) {
        super(brand, registrationNumber);//super calls parent class contructor
    }
    public void openTrunk() {
        System.out.println("Trunk opened");
    }
}
class Bike extends Vehicle {
    public Bike(String brand, String registrationNumber) {
        super(brand, registrationNumber);
    }
    public void doWheelie() {
        System.out.println("Doing wheelie");
    }
}
public class Main {
    public static void main(String[] args) {
        Car car = new Car("BMW", "KA-01");
        car.start();       // inherited
        car.openTrunk();   // Car-specific
    }
}
