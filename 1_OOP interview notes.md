# OOP Interview Notes (Java)

---

## 1. OOP FUNDAMENTALS

### 1.1 Class & Object

A class is a blueprint. An object is a runtime instance of that class, occupying memory on the heap.

```java
class Car {
    String brand;
    int speed;

    Car(String brand, int speed) {
        this.brand = brand;
        this.speed = speed;
    }

    int accelerate(int amount) {
        speed += amount;
        return speed;
    }
}

public class Main {
    public static void main(String[] args) {
        Car car1 = new Car("Toyota", 0);
        Car car2 = new Car("Honda", 0);
        car1.accelerate(20);
        System.out.println(car1.speed + " " + car2.speed); // 20 0 — independent state
    }
}
```

**Interview line:** "A class defines structure and behavior; an object is a concrete instantiation with its own state in memory."

---

### 1.2 Object Creation & Memory Basics

- `new Car(...)` allocates memory on the **heap** and returns a **reference** (pointer-like) stored on the **stack**.
- Object variables in Java always hold references, never the object itself.
- Assigning one reference to another copies the reference, not the object — both variables point to the same object.

```java
Car a = new Car("Toyota", 0);
Car b = a;          // b points to the SAME object as a
b.speed = 50;
System.out.println(a.speed); // 50 — because a and b reference the same memory
```

- Objects with no remaining references become eligible for **garbage collection**.
- Primitives (`int`, `double`, `boolean`, etc.) are stored directly on the stack (or inline in the object), not as references.

---

### 1.3 Constructor

Special method invoked automatically when an object is created. Used to initialize state.

```java
public class Point {
    private int x, y;

    Point() {                  // default constructor
        this(0, 0);
    }

    Point(int x, int y) {      // parameterized constructor
        this.x = x;
        this.y = y;
    }

    Point(Point other) {       // copy constructor (manual — Java has no built-in one)
        this.x = other.x;
        this.y = other.y;
    }
}
```

- **Default constructor** — no args; auto-provided by the compiler only if you define *no* constructor at all.
- **Parameterized constructor** — accepts args to set initial state.
- **Copy constructor** — Java doesn't auto-generate one (unlike C++); you write it yourself, or use `clone()`.

---

### 1.4 Constructor Chaining

Calling one constructor from another to avoid duplicated init logic.

```java
class Vehicle {
    String brand;
    int wheels;

    Vehicle(String brand) {
        this(brand, 4);              // chain to another constructor in same class
    }
    Vehicle(String brand, int wheels) {
        this.brand = brand;
        this.wheels = wheels;
    }
}

class Car extends Vehicle {
    String model;
    Car(String brand, String model) {
        super(brand);                // chain to parent constructor
        this.model = model;
    }
}
```

Rules: `this(...)` or `super(...)` must be the **first statement** in a constructor, and you can only call one of them (not both).

---

### 1.5 Destructor — Java Has None

Java has no deterministic destructor. The Garbage Collector reclaims memory automatically.

- `finalize()` — deprecated, timing not guaranteed, never rely on it.
- Correct pattern for cleanup (files, sockets, DB connections): **try-with-resources** + `AutoCloseable`.

```java
class FileHandler implements AutoCloseable {
    FileHandler() { System.out.println("opened"); }
    @Override
    public void close() { System.out.println("closed"); } // deterministic cleanup
}

try (FileHandler fh = new FileHandler()) {
    // use fh
} // close() called automatically here, even if an exception occurs
```

**Interview line:** "Java relies on GC for memory, so cleanup of external resources is done explicitly via try-with-resources / AutoCloseable, not destructors."

---

### 1.6 Pass-by-Value in Java

Java is **always pass-by-value** — but for objects, the "value" being passed is the reference (memory address), which creates the illusion of pass-by-reference.

```java
void modify(Car c) {
    c.speed = 100;      // mutates the SAME object — visible outside
    c = new Car("BMW", 0); // reassigns local copy of reference — invisible outside
}

Car myCar = new Car("Toyota", 0);
modify(myCar);
System.out.println(myCar.brand + " " + myCar.speed); // Toyota 100
```

**Interview line:** "Java passes a copy of the reference. You can mutate the object it points to, but reassigning the parameter inside the method doesn't affect the caller's variable."

---

### 1.7 Instance Variables vs Static Variables, Instance vs Static Methods

```java
class Employee {
    static String company = "Coupa";   // static — shared across ALL instances
    String name;                       // instance — unique per object
    double salary;

    Employee(String name, double salary) {
        this.name = name;
        this.salary = salary;
    }

    void raiseSalary(double amt) {     // instance method — needs an object, uses `this`
        salary += amt;
    }

    static void changeCompany(String newName) { // static method — no `this`, class-level
        company = newName;
    }
}

Employee e1 = new Employee("Alice", 90000);
Employee e2 = new Employee("Bob", 85000);
Employee.changeCompany("Coupa Inc");
System.out.println(e1.company + " " + e2.company); // Coupa Inc Coupa Inc
```

| | Instance | Static |
|---|---|---|
| Belongs to | Each object | The class itself |
| Memory | One copy per object | One copy total |
| Access | Needs an object (`obj.field`) | `ClassName.field` |
| Can use `this` | Yes | No |
| Access instance members | Yes | No (unless given an object) |

---

### 1.8 `this` vs `super`

- `this` — reference to the current object; disambiguates fields from parameters, chains constructors.
- `super` — reference to the parent class; calls parent's constructor or an overridden parent method explicitly.

```java
class Animal {
    String name;
    Animal(String name) { this.name = name; }
    String speak() { return "generic sound"; }
}

class Dog extends Animal {
    String breed;
    Dog(String name, String breed) {
        super(name);                    // call parent constructor
        this.breed = breed;
    }
    @Override
    String speak() {
        String parentSound = super.speak(); // call parent's overridden method
        return parentSound + ", but really: Woof";
    }
}
```

---

## 2. COPYING OBJECTS

### 2.1 Shallow Copy vs Deep Copy

- **Shallow copy** — copies the object, but nested object fields still point to the *same* referenced objects as the original.
- **Deep copy** — copies the object *and* recursively copies every nested object too, so the two are fully independent.

```java
class Engine {
    int hp;
    Engine(int hp) { this.hp = hp; }
}

class Car implements Cloneable {
    String brand;
    Engine engine;

    Car(String brand, Engine engine) {
        this.brand = brand;
        this.engine = engine;
    }

    // Shallow copy — default Object.clone() behavior
    @Override
    protected Car clone() throws CloneNotSupportedException {
        return (Car) super.clone();   // copies brand + engine REFERENCE, not a new Engine
    }

    // Deep copy — manual
    Car deepClone() {
        Engine newEngine = new Engine(this.engine.hp); // new independent Engine object
        return new Car(this.brand, newEngine);
    }
}

Car original = new Car("Toyota", new Engine(150));

Car shallow = original.clone();
shallow.engine.hp = 999;
System.out.println(original.engine.hp); // 999 — SAME engine object, original affected!

Car deep = original.deepClone();
deep.engine.hp = 1;
System.out.println(original.engine.hp); // still 999 — untouched, fully independent
```

**Interview line:** "Shallow copy duplicates the top-level object only; nested references are shared. Deep copy recursively duplicates nested objects so the copy is fully independent."

---

## 3. THE FOUR PILLARS OF OOP

### 3.1 Encapsulation

Bundling data + methods into a single unit, restricting direct access to internal state ("data hiding").

```java
public class BankAccount {
    private double balance;              // hidden

    public void deposit(double amt) {
        if (amt <= 0) throw new IllegalArgumentException("amount must be positive");
        balance += amt;
    }

    public double getBalance() {         // controlled access via getter
        return balance;
    }
}
```

**Data hiding vs Encapsulation:**
- Encapsulation = bundling data + behavior together (the broader OOP concept).
- Data hiding = the access-restriction mechanism (`private` fields + getters/setters) that *enforces* encapsulation.

#### 3.1.1 Immutable Class

An object whose state cannot change after construction. Rules:
1. Make the class `final` (can't be subclassed to add mutability).
2. Make all fields `private final`.
3. No setters.
4. If a field is a mutable object (e.g. a `List` or `Date`), return a **defensive copy** from the getter, not the original.

```java
public final class ImmutablePoint {
    private final int x;
    private final int y;

    public ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }

    // "Modifying" returns a NEW object instead of mutating this one
    public ImmutablePoint withX(int newX) {
        return new ImmutablePoint(newX, this.y);
    }
}
```

#### 3.1.2 Why `String` Is Immutable in Java

- **Security** — Strings are used for class names, file paths, network connections, DB URLs; if mutable, code could change a String after a security check (TOCTOU attack).
- **String pool / caching** — the JVM interns String literals in a shared pool; if Strings were mutable, changing one variable would corrupt the value seen by every other variable pointing at the same pooled literal.
- **Thread safety** — immutable objects are automatically safe to share across threads with no synchronization.
- **Hashcode caching** — `String.hashCode()` is computed once and cached, safe because the value never changes; this makes Strings fast, reliable `HashMap` keys.

```java
String s1 = "hello";
String s2 = s1;
s1 = s1 + " world";     // creates a NEW String object; does not mutate the original
System.out.println(s1); // hello world
System.out.println(s2); // hello — s2 still points to the original, untouched
```

---

### 3.2 Abstraction

Exposing only relevant behavior, hiding implementation detail. Achieved via abstract classes and interfaces.

```java
abstract class Shape {
    abstract double area();                 // abstract — no body, must be implemented

    void describe() {                       // concrete — shared implementation
        System.out.println("Area: " + area());
    }
}

class Circle extends Shape {
    double r;
    Circle(double r) { this.r = r; }

    @Override
    double area() { return Math.PI * r * r; }
}

// Shape s = new Shape(); // COMPILE ERROR — can't instantiate an abstract class
Circle c = new Circle(5);
c.describe(); // Area: 78.53...
```

---

### 3.3 Inheritance

A class (child/derived) acquires properties & behavior of another (parent/base).

```java
class Animal {
    String name;
    Animal(String name) { this.name = name; }
    String speak() { return "..."; }
}

class Dog extends Animal {                  // single inheritance
    Dog(String name) { super(name); }
    @Override
    String speak() { return name + " says Woof"; }
}

class Puppy extends Dog {                   // multilevel: Animal -> Dog -> Puppy
    Puppy(String name) { super(name); }
    @Override
    String speak() { return name + " yips"; }
}

class Cat extends Animal {                  // hierarchical: Animal -> Dog, Cat
    Cat(String name) { super(name); }
    @Override
    String speak() { return name + " says Meow"; }
}
```

**Types:**
- **Single** — one base, one derived (`Dog extends Animal`).
- **Multilevel** — chain: `Animal -> Dog -> Puppy`.
- **Hierarchical** — one base, multiple derived classes (`Dog`, `Cat` both extend `Animal`).
- **Multiple inheritance (of classes)** — **not supported in Java** (avoids the Diamond Problem). Achieved instead via implementing multiple interfaces.

#### 3.3.1 Upcasting vs Downcasting

- **Upcasting** — treating a child object as its parent type. Always safe, done implicitly.
- **Downcasting** — treating a parent-typed reference back as a child type. Not always safe; must be explicit, and can throw `ClassCastException` at runtime.

```java
Animal a = new Dog("Rex");          // upcasting — implicit, always safe
System.out.println(a.speak());      // "Rex says Woof" — dynamic dispatch still picks Dog's method

if (a instanceof Dog) {             // check before downcasting
    Dog d = (Dog) a;                // downcasting — explicit cast required
    System.out.println(d.name);
}

Animal cat = new Cat("Tom");
Dog wrong = (Dog) cat;              // compiles, but throws ClassCastException at runtime
```

---

### 3.4 Polymorphism

"Many forms" — same interface, different underlying implementation.

#### 3.4.1 Compile-time (static) polymorphism — Method Overloading

Same method name, different parameter list, resolved at compile time.

```java
class Calculator {
    int add(int a, int b) { return a + b; }
    double add(double a, double b) { return a + b; }      // overload: different types
    int add(int a, int b, int c) { return a + b + c; }    // overload: different arity
}
```

#### 3.4.2 Runtime (dynamic) polymorphism — Method Overriding

Subclass redefines a parent method with the **same signature**; resolved at runtime via dynamic dispatch.

```java
class Shape {
    double area() { return 0; }
}
class Rectangle extends Shape {
    double w, h;
    Rectangle(double w, double h) { this.w = w; this.h = h; }
    @Override
    double area() { return w * h; }
}
class Circle extends Shape {
    double r;
    Circle(double r) { this.r = r; }
    @Override
    double area() { return Math.PI * r * r; }
}

Shape[] shapes = { new Rectangle(3, 4), new Circle(5) };
for (Shape s : shapes) {
    System.out.println(s.area());   // correct overridden version called at runtime
}
```

**Overloading vs Overriding**

| | Overloading | Overriding |
|---|---|---|
| Binding | Compile-time (static) | Runtime (dynamic) |
| Where | Same class | Parent–child relationship |
| Signature | Must differ (params/types/arity) | Must be identical |
| Return type | Can differ | Must be same or covariant |
| Purpose | Same operation, different inputs | Specialize/replace inherited behavior |

#### 3.4.3 Method Hiding vs Overriding

- **Overriding** applies to **instance methods** — resolved at runtime based on the actual object type.
- **Method hiding** applies to **static methods** — a subclass can declare a static method with the same signature, but it's resolved at **compile time** based on the reference type, not overridden.

```java
class Parent {
    static void staticMethod() { System.out.println("Parent static"); }
    void instanceMethod() { System.out.println("Parent instance"); }
}
class Child extends Parent {
    static void staticMethod() { System.out.println("Child static"); }   // HIDING
    @Override
    void instanceMethod() { System.out.println("Child instance"); }      // OVERRIDING
}

Parent p = new Child();
p.staticMethod();     // "Parent static" — resolved by reference type (Parent) at compile time
p.instanceMethod();   // "Child instance" — resolved by actual object type at runtime
```

#### 3.4.4 Early Binding vs Late Binding

- **Early (static) binding** — method call resolved at **compile time**: applies to overloaded methods, `static`, `final`, `private` methods.
- **Late (dynamic) binding** — method call resolved at **runtime**, based on the actual object: applies to overridden instance methods (default in Java for non-static/non-final/non-private methods).

```java
Parent ref = new Child();
ref.staticMethod();    // early binding — decided by compiler using reference type
ref.instanceMethod();  // late binding — decided by JVM using actual object type (vtable lookup)
```

**Interview line:** "In Java, every non-static, non-final, non-private method is virtual by default and uses late binding. Static, final, and private methods, plus all overloaded calls, use early binding."

---

## 4. DEEP OOP — MUST KNOW COLD

### 4.1 Abstract Class vs Interface

| | Abstract Class | Interface |
|---|---|---|
| Methods | Abstract + concrete methods | Traditionally all abstract; Java 8+ allows `default`/`static` methods |
| State | Can have instance variables | No instance state (only `public static final` constants) |
| Constructor | Can have one | Cannot have one |
| Inheritance | Single (`extends` one class) | A class can `implement` many interfaces |
| Access modifiers | Any (`public`/`protected`/`private`) | Members implicitly `public` |
| Use when | Shared code/state + true IS-A hierarchy | A capability/contract across unrelated classes |

```java
interface Payable {
    double calculatePay();
}

abstract class Employee implements Payable {
    protected String name;
    Employee(String name) { this.name = name; }   // abstract classes CAN have constructors
    abstract double calculatePay();                // still abstract
    double calculateTax() { return calculatePay() * 0.2; } // shared concrete implementation
}

class Manager extends Employee {
    Manager(String name) { super(name); }
    @Override
    double calculatePay() { return 100000; }
}
```

**Interview line:** "Use an abstract class when subclasses share state/behavior in a true IS-A hierarchy. Use an interface for a contract unrelated classes can implement — Java doesn't allow multiple class inheritance, so interfaces give multiple-inheritance-like behavior."

---

### 4.2 Interface Default & Static Methods (Java 8+)

- **`default` method** — gives an interface a concrete body; implementing classes inherit it automatically but may override it.
- **`static` method** — belongs to the interface itself, called as `InterfaceName.method()`, cannot be overridden by implementers.

```java
interface Vehicle {
    void drive();

    default void honk() {                     // default method — has a body
        System.out.println("Beep beep!");
    }

    static Vehicle createDefault() {           // static method — factory-style utility
        return () -> System.out.println("Driving default vehicle");
    }
}

class Car implements Vehicle {
    @Override
    public void drive() { System.out.println("Car driving"); }
    // honk() inherited for free, or could be overridden
}

Vehicle v = new Car();
v.honk();                          // "Beep beep!" — inherited default
Vehicle.createDefault().drive();   // called directly on the interface
```

Default methods were added so interfaces could evolve (add new methods) without breaking every existing implementing class.

---

### 4.3 Multiple Interfaces

A single class can implement many interfaces — Java's answer to multiple inheritance.

```java
interface Flyer {
    default String move() { return "flying"; }
}
interface Swimmer {
    default String move() { return "swimming"; }
}

class Duck implements Flyer, Swimmer {
    @Override
    public String move() {          // MUST override — resolves the ambiguity yourself
        return Flyer.super.move() + " and " + Swimmer.super.move();
    }
}
```

If two interfaces provide the same `default` method, the implementing class is **forced to override it** and resolve the conflict explicitly (unlike Python's automatic MRO resolution) — this is how Java avoids the diamond problem.

---

### 4.4 Composition vs Inheritance ("favor composition over inheritance")

- **Inheritance (IS-A)** — tight coupling; subclass depends on parent's implementation details; can break with parent changes (fragile base class problem).
- **Composition (HAS-A)** — an object holds references to other objects and delegates work to them; flexible, swappable at runtime.

```java
// Inheritance approach — WRONG, Car is-not-an Engine
class Engine {
    String start() { return "Engine starting"; }
}
class BadCar extends Engine { }

// Composition approach — correct, Car HAS-A Engine
class Car {
    private Engine engine;
    Car(Engine engine) { this.engine = engine; }
    String start() { return engine.start(); }
}

Car car = new Car(new Engine());
System.out.println(car.start());
```

The **Strategy pattern** is a classic example: instead of subclassing for every behavior variant, you inject a behavior object.

---

### 4.5 Association, Aggregation, Composition (with lifecycle code)

All three describe "HAS-A" relationships; they differ in **ownership** and **lifecycle**.

- **Association** — general relationship, objects know about each other, no ownership, independent lifecycles.
- **Aggregation** — "weak HAS-A". A whole contains parts, but the parts can exist independently and outlive the whole.
- **Composition** — "strong HAS-A". The whole owns the parts; parts' lifecycle is bound to the whole — destroy the whole, and the parts are destroyed too.

```java
// ASSOCIATION — Student just "uses" a Teacher; neither owns the other
class Teacher {
    String name;
    Teacher(String name) { this.name = name; }
}
class Student {
    Teacher teacher;                      // association — no ownership
    Student(Teacher teacher) { this.teacher = teacher; }
}

// AGGREGATION — Department has Professors, but Professors exist independently
class Professor {
    String name;
    Professor(String name) { this.name = name; }
}
class Department {
    List<Professor> professors = new ArrayList<>();
    void add(Professor p) { professors.add(p); }   // Professor created OUTSIDE, just referenced
}
Professor rao = new Professor("Dr. Rao");
Department cs = new Department();
cs.add(rao);
// if `cs` is discarded, `rao` still exists independently and could join another department

// COMPOSITION — Heart is created and dies WITH the Human; no independent lifecycle
class Heart {
    String beat() { return "thump"; }
}
class Human {
    private final Heart heart = new Heart();   // Heart created INSIDE, owned exclusively
    String heartbeat() { return heart.beat(); }
}
// when a Human object is garbage collected, its Heart is collected too — no external reference exists
```

**Strength ranking:** Association (weakest) → Aggregation → Composition (strongest, exclusive ownership + shared lifecycle).

---

### 4.6 `static` Keyword

Belongs to the class, not any instance. Shared across all objects, exists without instantiation.

```java
class Counter {
    static int count = 0;                    // one copy shared by all instances
    Counter() { count++; }
    static int getCount() { return count; }   // static method — can't use `this`
}
new Counter();
new Counter();
System.out.println(Counter.getCount());  // 2
```

---

### 4.7 `final` Keyword

- **`final` variable** — value can't be reassigned after initialization (constant).
- **`final` method** — cannot be overridden by subclasses.
- **`final` class** — cannot be subclassed at all (e.g. `String`, `Integer`).

```java
final class ImmutablePoint {               // can't be extended
    private final int x, y;                // can't be reassigned after constructor
    ImmutablePoint(int x, int y) { this.x = x; this.y = y; }
    final int getX() { return x; }         // can't be overridden (redundant since class is final)
}
```

#### 4.7.1 `final` vs `finally` vs `finalize`

| | `final` | `finally` | `finalize()` |
|---|---|---|---|
| What it is | Keyword/modifier | Block in try-catch | Method on `Object` |
| Purpose | Prevent reassignment/override/subclassing | Code that ALWAYS runs after try/catch, used for cleanup | Called by GC before reclaiming an object (deprecated) |
| Used on | Variables, methods, classes | try-catch-finally structure | Overridden in a class |
| Guaranteed to run? | N/A | Yes (except `System.exit()` or JVM crash) | No — timing/occurrence not guaranteed |

```java
try {
    riskyOperation();
} catch (Exception e) {
    System.out.println("caught: " + e.getMessage());
} finally {
    System.out.println("always runs — cleanup here");
}
```

---

### 4.8 Virtual Functions & Dynamic Dispatch in Java

Java has no `virtual` keyword — **every non-static, non-final, non-private method is virtual by default** and uses dynamic dispatch automatically.

```java
class Animal {
    void speak() { System.out.println("..."); }        // implicitly virtual
    final void nonOverridable() { System.out.println("base"); } // final -> NOT virtual
}
class Dog extends Animal {
    @Override
    void speak() { System.out.println("Woof"); }
    // cannot override nonOverridable() — compile error
}

Animal a = new Dog();
a.speak();             // "Woof" — dynamic dispatch based on actual object type (Dog)
```

**Mechanism:** implemented via a **vtable** (virtual method table) — each object carries a pointer to a table of method implementations; the JVM looks up the correct implementation through that table based on the object's real class at runtime.

---

## 5. THE `Object` CLASS & CORE METHODS

Every class in Java implicitly extends `Object`, which provides `equals()`, `hashCode()`, `toString()`, `clone()`, `getClass()`, `wait()`/`notify()`, etc.

### 5.1 `==` vs `.equals()`

- **`==`** — compares references (memory addresses) for objects; compares actual values for primitives.
- **`.equals()`** — compares logical/content equality; default `Object.equals()` behaves like `==` unless overridden (as `String`, `Integer`, etc. do).

```java
String s1 = new String("hello");
String s2 = new String("hello");

System.out.println(s1 == s2);         // false — different objects in memory
System.out.println(s1.equals(s2));    // true — same content

int x = 5, y = 5;
System.out.println(x == y);           // true — primitives compare by value
```

### 5.2 `equals()` vs `hashCode()`

The **contract**: if two objects are equal via `.equals()`, they **must** return the same `hashCode()`. Breaking this contract silently corrupts hash-based collections (`HashMap`, `HashSet`).

```java
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);      // must match equals() logic
    }
}

Set<Point> set = new HashSet<>();
set.add(new Point(1, 2));
System.out.println(set.contains(new Point(1, 2))); // true — only works because BOTH are overridden correctly
```

**Interview line:** "If you override `equals()` without `hashCode()`, two 'equal' objects can land in different hash buckets, so `HashSet`/`HashMap` will treat them as different — always override both together."

---

## 6. ACCESS MODIFIERS (Java)

| Modifier | Class | Package | Subclass (diff package) | World |
|---|---|---|---|---|
| `public` | Yes | Yes | Yes | Yes |
| `protected` | Yes | Yes | Yes | No |
| *(default / package-private)* | Yes | Yes | No | No |
| `private` | Yes | No | No | No |

```java
public class Account {
    private double balance;         // only this class
    protected String owner;         // this class + package + subclasses
    String branch;                  // package-private (default) — only same package
    public String accountId;        // accessible everywhere
}
```

---

## 7. DESIGN PRINCIPLES

### 7.1 Coupling & Cohesion

- **Coupling** — how much one class/module depends on the internal details of another. **Low coupling** is desirable (classes interact through clean interfaces, not internals).
- **Cohesion** — how focused a class/module is on a single responsibility. **High cohesion** is desirable (a class does one thing well).

```java
// LOW cohesion, HIGH coupling — bad
class ReportManager {
    void fetchData() { /* db logic */ }
    void formatData() { /* formatting logic */ }
    void sendEmail() { /* email logic */ }   // unrelated responsibility crammed in
}

// HIGH cohesion, LOW coupling — good: each class does one job, talks via interfaces
class DataFetcher { void fetch() { } }
class ReportFormatter { void format() { } }
class EmailSender { void send() { } }
```

### 7.2 SOLID Principles

| Letter | Principle | Meaning |
|---|---|---|
| **S** | Single Responsibility | A class should have only one reason to change |
| **O** | Open/Closed | Open for extension, closed for modification |
| **L** | Liskov Substitution | Subtypes must be substitutable for their base type without breaking behavior |
| **I** | Interface Segregation | Prefer many small interfaces over one bloated one |
| **D** | Dependency Inversion | Depend on abstractions, not concrete implementations |

```java
// O — Open/Closed: add new shapes WITHOUT modifying AreaCalculator
interface Shape { double area(); }
class Circle implements Shape {
    double r;
    Circle(double r) { this.r = r; }
    public double area() { return Math.PI * r * r; }
}
class Square implements Shape {
    double side;
    Square(double side) { this.side = side; }
    public double area() { return side * side; }
}
class AreaCalculator {
    double totalArea(List<Shape> shapes) {
        double total = 0;
        for (Shape s : shapes) total += s.area();   // no changes needed for new shapes
        return total;
    }
}
```

```java
// L — Liskov Substitution VIOLATION example (classic gotcha)
class Rectangle {
    protected int w, h;
    void setWidth(int w) { this.w = w; }
    void setHeight(int h) { this.h = h; }
    int area() { return w * h; }
}
class Square extends Rectangle {
    @Override void setWidth(int w) { this.w = this.h = w; }   // breaks expected Rectangle behavior
    @Override void setHeight(int h) { this.w = this.h = h; }
}
// Code that assumes "setWidth then setHeight gives independent w,h" breaks silently for Square
```

### 7.3 Dependency Injection (DI)

Instead of a class creating its own dependencies, they're "injected" from outside (constructor, setter, or a DI framework like Spring) — reduces coupling, improves testability.

```java
interface PaymentService {
    void pay(double amount);
}
class CreditCardService implements PaymentService {
    public void pay(double amount) { System.out.println("Paid " + amount + " via card"); }
}

class Checkout {
    private final PaymentService paymentService;

    // constructor injection — dependency provided from OUTSIDE, not created internally
    Checkout(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    void completeOrder(double amount) {
        paymentService.pay(amount);
    }
}

Checkout checkout = new Checkout(new CreditCardService());  // easy to swap in a mock for testing
checkout.completeOrder(99.99);
```

### 7.4 Dependency Inversion / Interface-Based Design

High-level modules should not depend on low-level modules — both should depend on abstractions. This is the "D" in SOLID, and DI is the common technique used to achieve it.

```java
// WITHOUT inversion — high-level class depends directly on a concrete low-level class
class MySQLDatabase {
    void save(String data) { System.out.println("Saved to MySQL: " + data); }
}
class UserService {
    private MySQLDatabase db = new MySQLDatabase();   // tightly coupled to MySQL specifically
    void save(String data) { db.save(data); }
}

// WITH inversion — both depend on an abstraction
interface Database {
    void save(String data);
}
class MySQLDatabase2 implements Database {
    public void save(String data) { System.out.println("Saved to MySQL: " + data); }
}
class MongoDatabase implements Database {
    public void save(String data) { System.out.println("Saved to Mongo: " + data); }
}
class UserService2 {
    private final Database db;
    UserService2(Database db) { this.db = db; }        // depends on the interface, not a concrete DB
    void save(String data) { db.save(data); }
}
// swap databases without touching UserService2 at all
UserService2 service = new UserService2(new MongoDatabase());
```

---

## 8. DESIGN PATTERNS

### 8.1 Singleton — Basic Implementation

Ensures a class has exactly **one instance**, with a global access point.

```java
class Singleton {
    private static Singleton instance;   // holds the single instance

    private Singleton() { }              // private constructor — no `new` from outside

    public static synchronized Singleton getInstance() {  // synchronized = thread-safe
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}

Singleton a = Singleton.getInstance();
Singleton b = Singleton.getInstance();
System.out.println(a == b); // true — same instance
```

For high-performance thread safety, the common upgrade is the **double-checked locking** or **eager initialization** (`private static final Singleton instance = new Singleton();`) or an `enum` singleton.

---

## 9. EXCEPTION HANDLING

Java uses a class hierarchy: `Throwable` → `Exception` (checked) / `RuntimeException` (unchecked) → your custom exceptions.

```java
// Custom checked exception — caller MUST handle or declare it
class InsufficientFundsException extends Exception {
    InsufficientFundsException(String message) {
        super(message);
    }
}

class BankAccount {
    private double balance;

    void withdraw(double amount) throws InsufficientFundsException {
        if (amount > balance) {
            throw new InsufficientFundsException("Not enough balance");
        }
        balance -= amount;
    }
}

try {
    new BankAccount().withdraw(100);
} catch (InsufficientFundsException e) {
    System.out.println("Error: " + e.getMessage());
} finally {
    System.out.println("Transaction attempt finished");
}
```

**Checked vs Unchecked:**

| | Checked | Unchecked |
|---|---|---|
| Extends | `Exception` (not `RuntimeException`) | `RuntimeException` |
| Compiler enforces handling | Yes — must `catch` or `throws` | No |
| Example | `IOException`, custom business exceptions | `NullPointerException`, `ArrayIndexOutOfBoundsException` |
| Typical use | Recoverable conditions caller should plan for | Programming bugs |

---

## 10. GENERICS

Let classes/methods operate on types specified by the caller, with compile-time type safety (no casting, no `ClassCastException` surprises).

```java
class Box<T> {
    private T content;
    void set(T content) { this.content = content; }
    T get() { return content; }
}

Box<String> stringBox = new Box<>();
stringBox.set("hello");
String s = stringBox.get();     // no cast needed, type-safe at compile time

// Generic method
static <T> T firstElement(List<T> list) {
    return list.get(0);
}

// Bounded type parameter
static <T extends Number> double sum(List<T> list) {
    double total = 0;
    for (T item : list) total += item.doubleValue();
    return total;
}
```

**Interview line:** "Generics give compile-time type safety and eliminate the need for manual casting; before Java 5, collections stored raw `Object`s and casts could fail at runtime."

---

## 11. COLLECTIONS & HOW OOP CONCEPTS APPLY

The Collections Framework is a textbook example of OOP in practice:

- **Abstraction/Polymorphism** — code programs to the `List`, `Set`, `Map` interfaces, not concrete classes (`ArrayList`, `HashSet`, `HashMap`), so implementations are swappable.
- **Inheritance** — e.g. `ArrayList` implements `List`, which extends `Collection`, which extends `Iterable`.
- **Encapsulation** — internal array/tree/hash-table structure is hidden behind a clean public API.

```java
List<String> names = new ArrayList<>();   // program to the interface
names.add("Alice");
names.add("Bob");

names = new LinkedList<>();               // swap implementation — rest of code unchanged
names.add("Carol");

for (String name : names) {               // Iterable — polymorphic iteration
    System.out.println(name);
}
```

`equals()`/`hashCode()` correctness (Section 5.2) directly determines whether `HashSet`/`HashMap` behave correctly with custom objects as elements/keys.

---

## 12. COMMON OOP DESIGN / INTERVIEW QUESTIONS (Rapid Fire)

- **Why can't we instantiate an abstract class?** — It may have unimplemented (abstract) methods with no body; calling them would have no defined behavior.
- **Can a constructor be `private`?** — Yes, used in Singleton pattern and static factory methods to control instantiation.
- **Can an interface have a constructor?** — No, interfaces cannot hold state to initialize.
- **Can we override a `static` method?** — No, it's hidden, not overridden (see 3.4.3).
- **Can we override a `private` method?** — No, private methods aren't visible to subclasses at all.
- **What happens if a subclass doesn't implement all abstract methods?** — It must also be declared `abstract`, or it won't compile.
- **Why does Java not support multiple inheritance of classes?** — To avoid the Diamond Problem (ambiguity over which parent's method/field wins); interfaces solve this because implementers must explicitly resolve conflicting `default` methods.
- **Is Java "pass-by-reference"?** — No, always pass-by-value; see Section 1.6.
- **Difference between `String`, `StringBuilder`, `StringBuffer`?** — `String` is immutable; `StringBuilder` is mutable and not thread-safe (faster); `StringBuffer` is mutable and thread-safe (synchronized, slower).
- **What is the diamond problem, and how does Java handle it for interfaces?** — When a class implements two interfaces with the same `default` method, Java forces the class to override and explicitly resolve it (see 4.3).
---

## 4. OOP DESIGN (LOW-LEVEL DESIGN) ⭐⭐⭐⭐⭐

General approach for any LLD interview question:
1. **Clarify requirements** (scope, scale, edge cases) before coding.
2. **Identify nouns → classes**, **verbs → methods**.
3. **Identify relationships**: association / aggregation / composition / inheritance.
4. Apply **SOLID** where relevant (especially Open/Closed via interfaces/strategy, and Single Responsibility).
5. Code the skeleton: classes, key methods, enums, relationships — not full business logic unless asked.
6. Discuss **extensibility**: "how would you add X?"

---

# LLD Coding Examples — Easy Java

These are simplified Java versions suitable for a fresher technical interview.

---

# 1. Parking Lot

## Requirements

* Multiple floors
* Different parking spots
* Cars, motorcycles, trucks
* Park vehicle
* Generate ticket
* Remove vehicle
* Calculate fee

## Main Classes

```text
ParkingLot
    ↓
Floor
    ↓
ParkingSpot
    ↓
Vehicle
    ├── Car
    ├── Motorcycle
    └── Truck

Ticket
Payment
```

## Code

```java
import java.util.*;

enum VehicleType {
    CAR,
    MOTORCYCLE,
    TRUCK
}

enum SpotType {
    COMPACT,
    LARGE,
    MOTORCYCLE
}
```

### Vehicle

```java
class Vehicle {

    String number;
    VehicleType type;

    Vehicle(String number, VehicleType type) {
        this.number = number;
        this.type = type;
    }
}
```

### Car, Motorcycle, Truck

```java
class Car extends Vehicle {

    Car(String number) {
        super(number, VehicleType.CAR);
    }
}

class Motorcycle extends Vehicle {

    Motorcycle(String number) {
        super(number, VehicleType.MOTORCYCLE);
    }
}

class Truck extends Vehicle {

    Truck(String number) {
        super(number, VehicleType.TRUCK);
    }
}
```

### Parking Spot

```java
class ParkingSpot {

    int id;
    SpotType type;
    Vehicle vehicle;

    ParkingSpot(int id, SpotType type) {
        this.id = id;
        this.type = type;
    }

    boolean isFree() {
        return vehicle == null;
    }

    boolean canFit(Vehicle vehicle) {

        if (vehicle.type == VehicleType.MOTORCYCLE) {
            return true;
        }

        if (vehicle.type == VehicleType.CAR) {
            return type == SpotType.COMPACT ||
                   type == SpotType.LARGE;
        }

        if (vehicle.type == VehicleType.TRUCK) {
            return type == SpotType.LARGE;
        }

        return false;
    }

    void park(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    void remove() {
        this.vehicle = null;
    }
}
```

### Floor

```java
class Floor {

    int floorNumber;
    List<ParkingSpot> spots;

    Floor(int floorNumber, List<ParkingSpot> spots) {
        this.floorNumber = floorNumber;
        this.spots = spots;
    }

    ParkingSpot findSpot(Vehicle vehicle) {

        for (ParkingSpot spot : spots) {

            if (spot.isFree() && spot.canFit(vehicle)) {
                return spot;
            }
        }

        return null;
    }
}
```

### Ticket

```java
class Ticket {

    int ticketId;
    Vehicle vehicle;
    ParkingSpot spot;
    long entryTime;

    Ticket(int ticketId, Vehicle vehicle, ParkingSpot spot) {

        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = System.currentTimeMillis();
    }
}
```

### Payment

```java
class Payment {

    static double calculateFee(Ticket ticket) {

        long currentTime = System.currentTimeMillis();

        long time = currentTime - ticket.entryTime;

        long hours = time / (1000 * 60 * 60);

        // Minimum 1 hour
        hours = Math.max(1, hours);

        return hours * 20;
    }
}
```

### Parking Lot

```java
class ParkingLot {

    List<Floor> floors;
    Map<Integer, Ticket> tickets = new HashMap<>();

    int nextTicketId = 1;

    ParkingLot(List<Floor> floors) {
        this.floors = floors;
    }

    Ticket parkVehicle(Vehicle vehicle) {

        for (Floor floor : floors) {

            ParkingSpot spot = floor.findSpot(vehicle);

            if (spot != null) {

                spot.park(vehicle);

                Ticket ticket =
                    new Ticket(nextTicketId++, vehicle, spot);

                tickets.put(ticket.ticketId, ticket);

                return ticket;
            }
        }

        return null;
    }

    double removeVehicle(int ticketId) {

        Ticket ticket = tickets.remove(ticketId);

        if (ticket == null) {
            return -1;
        }

        ticket.spot.remove();

        return Payment.calculateFee(ticket);
    }
}
```

### Usage

```java
public class Main {

    public static void main(String[] args) {

        List<ParkingSpot> spots = new ArrayList<>();

        spots.add(new ParkingSpot(1, SpotType.COMPACT));
        spots.add(new ParkingSpot(2, SpotType.COMPACT));
        spots.add(new ParkingSpot(3, SpotType.LARGE));

        Floor floor = new Floor(1, spots);

        ParkingLot lot =
            new ParkingLot(Arrays.asList(floor));

        Vehicle car = new Car("KA01AB1234");

        Ticket ticket = lot.parkVehicle(car);

        System.out.println("Ticket ID: " + ticket.ticketId);

        double fee = lot.removeVehicle(ticket.ticketId);

        System.out.println("Fee: " + fee);
    }
}
```

### OOP concepts demonstrated

```text
Vehicle → inheritance
ParkingLot → has Floors
Floor → has ParkingSpots
ParkingSpot → associated with Vehicle
Ticket → associated with Vehicle + Spot
Payment → separate responsibility
```

### Interview extensions

If asked how to improve it:

* Add `PricingStrategy` interface for different pricing.
* Add `SpotAssignmentStrategy` for nearest spot.
* Add `Singleton` for one parking lot.
* Add different pricing for car/truck/motorcycle.

---

# 2. Elevator System

## Requirements

* Multiple elevators
* Multiple floors
* User requests elevator
* Elevator moves
* User selects destination
* Controller decides which elevator to use

## Main Classes

```text
ElevatorController
        ↓
     Elevator
        ↓
       Door

Request
Direction
```

## Code

### Direction

```java
enum Direction {
    UP,
    DOWN,
    IDLE
}
```

### Door

```java
class Door {

    boolean open = false;

    void open() {
        open = true;
        System.out.println("Door opened");
    }

    void close() {
        open = false;
        System.out.println("Door closed");
    }
}
```

### Request

```java
class Request {

    int floor;

    Request(int floor) {
        this.floor = floor;
    }
}
```

### Elevator

```java
class Elevator {

    int id;
    int currentFloor;
    Direction direction;

    Door door;
    Queue<Request> requests;

    Elevator(int id) {

        this.id = id;
        this.currentFloor = 1;
        this.direction = Direction.IDLE;

        door = new Door();
        requests = new LinkedList<>();
    }

    void addRequest(int floor) {

        requests.add(new Request(floor));
    }

    void move() {

        if (requests.isEmpty()) {
            direction = Direction.IDLE;
            return;
        }

        int target = requests.peek().floor;

        if (currentFloor < target) {

            direction = Direction.UP;
            currentFloor++;

        } else if (currentFloor > target) {

            direction = Direction.DOWN;
            currentFloor++;
        }

        if (currentFloor == target) {

            door.open();

            requests.poll();

            door.close();

            direction = Direction.IDLE;
        }
    }
}
```

**Important correction for an interview:** when moving down, `currentFloor` should decrease:

```java
else if (currentFloor > target) {
    direction = Direction.DOWN;
    currentFloor--;
}
```

Use that version.

### Elevator Controller

```java
class ElevatorController {

    List<Elevator> elevators;

    ElevatorController(int numberOfElevators) {

        elevators = new ArrayList<>();

        for (int i = 0; i < numberOfElevators; i++) {
            elevators.add(new Elevator(i));
        }
    }

    int requestElevator(int floor) {

        Elevator best = elevators.get(0);

        int minimumDistance =
            Math.abs(best.currentFloor - floor);

        for (Elevator elevator : elevators) {

            int distance =
                Math.abs(elevator.currentFloor - floor);

            if (distance < minimumDistance) {

                minimumDistance = distance;
                best = elevator;
            }
        }

        best.addRequest(floor);

        return best.id;
    }

    void selectFloor(int elevatorId, int floor) {

        elevators.get(elevatorId).addRequest(floor);
    }
}
```

### Usage

```java
public class Main {

    public static void main(String[] args) {

        ElevatorController controller =
            new ElevatorController(3);

        int elevatorId =
            controller.requestElevator(5);

        controller.selectFloor(elevatorId, 9);

        Elevator elevator =
            controller.elevators.get(elevatorId);

        while (!elevator.requests.isEmpty()) {
            elevator.move();
        }
    }
}
```

### OOP concepts

```text
Controller → manages Elevators
Elevator → has Door
Elevator → has Requests
Request → stores floor information
```

### Interview extensions

You can say:

> "For a real system, instead of simply selecting the nearest elevator, I could create a `DispatchStrategy` interface and implement nearest elevator, same-direction elevator, zoning, etc."

---

# 3. Library Management System

## Requirements

* Add books
* Multiple copies of the same book
* Search books
* Borrow book
* Return book
* Track due date
* Calculate fine
* Librarian can issue/return books

## Important design

Don't make only:

```text
Book
```

Instead:

```text
Book = information about the title

BookItem = actual physical copy
```

For example:

```text
Book:
"Clean Code"

BookItems:
Copy 1
Copy 2
Copy 3
```

This is a very good interview point.

---

## Person

```java
class Person {

    String name;
    String id;

    Person(String name, String id) {
        this.name = name;
        this.id = id;
    }
}
```

## User

```java
class User extends Person {

    List<BookItem> borrowedBooks = new ArrayList<>();

    User(String name, String id) {
        super(name, id);
    }
}
```

## Librarian

```java
class Librarian extends Person {

    Librarian(String name, String id) {
        super(name, id);
    }

    void issueBook(Library library,
                   BookItem book,
                   User user) {

        library.issueBook(book, user);
    }

    void returnBook(Library library,
                    BookItem book) {

        library.returnBook(book);
    }
}
```

## Book

```java
class Book {

    String isbn;
    String title;
    String author;

    Book(String isbn, String title, String author) {

        this.isbn = isbn;
        this.title = title;
        this.author = author;
    }
}
```

## BookItem

```java
class BookItem {

    String barcode;
    Book book;

    boolean available = true;

    BookItem(String barcode, Book book) {

        this.barcode = barcode;
        this.book = book;
    }
}
```

## Loan

```java
class Loan {

    BookItem book;
    User user;

    long issueTime;

    Loan(BookItem book, User user) {

        this.book = book;
        this.user = user;

        issueTime = System.currentTimeMillis();
    }

    double calculateFine() {

        long currentTime = System.currentTimeMillis();

        long days =
            (currentTime - issueTime)
            / (1000 * 60 * 60 * 24);

        if (days <= 14) {
            return 0;
        }

        return (days - 14) * 5;
    }
}
```

## Library

```java
class Library {

    Map<String, Book> books = new HashMap<>();

    List<BookItem> items = new ArrayList<>();

    Map<String, Loan> activeLoans = new HashMap<>();

    void addBook(Book book, int copies) {

        books.put(book.isbn, book);

        for (int i = 1; i <= copies; i++) {

            String barcode =
                book.isbn + "-" + i;

            items.add(
                new BookItem(barcode, book)
            );
        }
    }

    List<Book> search(String title) {

        List<Book> result = new ArrayList<>();

        for (Book book : books.values()) {

            if (book.title
                .toLowerCase()
                .contains(title.toLowerCase())) {

                result.add(book);
            }
        }

        return result;
    }

    Loan issueBook(BookItem item, User user) {

        if (!item.available) {
            return null;
        }

        item.available = false;

        Loan loan = new Loan(item, user);

        activeLoans.put(item.barcode, loan);

        user.borrowedBooks.add(item);

        return loan;
    }

    double returnBook(BookItem item) {

        Loan loan =
            activeLoans.remove(item.barcode);

        if (loan == null) {
            return 0;
        }

        item.available = true;

        loan.user.borrowedBooks.remove(item);

        return loan.calculateFine();
    }
}
```

## Usage

```java
public class Main {

    public static void main(String[] args) {

        Library library = new Library();

        Book book =
            new Book(
                "978-1",
                "Clean Code",
                "Robert Martin"
            );

        library.addBook(book, 2);

        User user =
            new User("Alice", "U1");

        Librarian librarian =
            new Librarian("Rao", "L1");

        BookItem item =
            library.items.get(0);

        librarian.issueBook(
            library,
            item,
            user
        );

        double fine =
            library.returnBook(item);

        System.out.println("Fine: " + fine);
    }
}
```

### OOP concepts

```text
Person
 ├── User
 └── Librarian

Library → has Books/BookItems
Book → title information
BookItem → physical copy
Loan → connects User + BookItem
```

### Interview extensions

Add:

* Reservation
* E-books
* Notification service
* Multiple library branches
* Different user types
* Maximum borrowing limits

---

# 4. ATM System

## Requirements

* Insert card
* Validate PIN
* Check balance
* Withdraw
* Deposit
* Dispense cash
* Store transactions

## Main Classes

```text
ATM
 ├── CashDispenser
 └── Account

Card
Account
Transaction
```

---

## Transaction Type

```java
enum TransactionType {
    WITHDRAW,
    DEPOSIT
}
```

## Card

```java
class Card {

    String cardNumber;
    String pin;
    String accountNumber;

    Card(String cardNumber,
         String pin,
         String accountNumber) {

        this.cardNumber = cardNumber;
        this.pin = pin;
        this.accountNumber = accountNumber;
    }

    boolean validatePin(String enteredPin) {

        return pin.equals(enteredPin);
    }
}
```

## Transaction

```java
class Transaction {

    TransactionType type;
    double amount;

    Transaction(TransactionType type,
                double amount) {

        this.type = type;
        this.amount = amount;
    }
}
```

## Account

```java
class Account {

    String accountNumber;
    double balance;

    List<Transaction> transactions =
        new ArrayList<>();

    Account(String accountNumber,
            double balance) {

        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    void deposit(double amount) {

        balance += amount;

        transactions.add(
            new Transaction(
                TransactionType.DEPOSIT,
                amount
            )
        );
    }

    boolean withdraw(double amount) {

        if (amount > balance) {
            return false;
        }

        balance -= amount;

        transactions.add(
            new Transaction(
                TransactionType.WITHDRAW,
                amount
            )
        );

        return true;
    }
}
```

## Cash Dispenser

Keep this simple for an interview:

```java
class CashDispenser {

    int cash = 100000;

    boolean canDispense(int amount) {

        return amount <= cash &&
               amount % 100 == 0;
    }

    boolean dispense(int amount) {

        if (!canDispense(amount)) {
            return false;
        }

        cash -= amount;

        System.out.println(
            "Cash dispensed: " + amount
        );

        return true;
    }
}
```

## ATM

```java
class ATM {

    CashDispenser dispenser =
        new CashDispenser();

    Map<String, Account> accounts =
        new HashMap<>();

    Account currentAccount;

    void addAccount(Account account) {

        accounts.put(
            account.accountNumber,
            account
        );
    }

    boolean insertCard(Card card,
                       String enteredPin) {

        if (!card.validatePin(enteredPin)) {

            System.out.println("Wrong PIN");
            return false;
        }

        currentAccount =
            accounts.get(card.accountNumber);

        return true;
    }

    void checkBalance() {

        System.out.println(
            "Balance: " +
            currentAccount.balance
        );
    }

    void withdraw(int amount) {

        if (!dispenser.canDispense(amount)) {

            System.out.println(
                "Cannot dispense cash"
            );

            return;
        }

        if (!currentAccount.withdraw(amount)) {

            System.out.println(
                "Insufficient balance"
            );

            return;
        }

        dispenser.dispense(amount);

        System.out.println(
            "Remaining balance: " +
            currentAccount.balance
        );
    }

    void deposit(double amount) {

        currentAccount.deposit(amount);
    }

    void ejectCard() {

        currentAccount = null;
    }
}
```

## Usage

```java
public class Main {

    public static void main(String[] args) {

        ATM atm = new ATM();

        Account account =
            new Account("A1", 10000);

        atm.addAccount(account);

        Card card =
            new Card(
                "1234",
                "4321",
                "A1"
            );

        atm.insertCard(card, "4321");

        atm.checkBalance();

        atm.withdraw(2000);

        atm.deposit(1000);

        atm.checkBalance();

        atm.ejectCard();
    }
}
```

### OOP concepts

```text
ATM → has CashDispenser
ATM → works with Account
Card → authenticates User
Account → has Transactions
```

### Important interview point

If asked:

> "How would you make the ATM design better?"

Say:

> "I would model the ATM using the State pattern, with states such as Idle, CardInserted, Authenticated and Transaction. This avoids a large number of if-else conditions."

---

# 5. Tic-Tac-Toe

## Requirements

* Two players
* 3×3 board
* X and O
* Alternate turns
* Detect winner
* Detect draw

## Main Classes

```text
Game
 ├── Board
 ├── Player
 └── Move
```

---

## Symbol

```java
enum Symbol {
    X,
    O
}
```

## Player

```java
class Player {

    String name;
    Symbol symbol;

    Player(String name, Symbol symbol) {

        this.name = name;
        this.symbol = symbol;
    }
}
```

## Move

```java
class Move {

    int row;
    int col;
    Symbol symbol;

    Move(int row, int col, Symbol symbol) {

        this.row = row;
        this.col = col;
        this.symbol = symbol;
    }
}
```

## Board

```java
class Board {

    int size;
    Symbol[][] board;

    Board(int size) {

        this.size = size;

        board = new Symbol[size][size];

        for (int i = 0; i < size; i++) {

            for (int j = 0; j < size; j++) {

                board[i][j] = null;
            }
        }
    }

    boolean placeMove(Move move) {

        if (board[move.row][move.col] != null) {
            return false;
        }

        board[move.row][move.col] =
            move.symbol;

        return true;
    }

    boolean isFull() {

        for (int i = 0; i < size; i++) {

            for (int j = 0; j < size; j++) {

                if (board[i][j] == null) {
                    return false;
                }
            }
        }

        return true;
    }

    Symbol checkWinner() {

        // Rows
        for (int i = 0; i < size; i++) {

            if (board[i][0] == null) {
                continue;
            }

            boolean same = true;

            for (int j = 1; j < size; j++) {

                if (board[i][j] != board[i][0]) {
                    same = false;
                    break;
                }
            }

            if (same) {
                return board[i][0];
            }
        }

        // Columns
        for (int j = 0; j < size; j++) {

            if (board[0][j] == null) {
                continue;
            }

            boolean same = true;

            for (int i = 1; i < size; i++) {

                if (board[i][j] != board[0][j]) {
                    same = false;
                    break;
                }
            }

            if (same) {
                return board[0][j];
            }
        }

        // Main diagonal
        if (board[0][0] != null) {

            boolean same = true;

            for (int i = 1; i < size; i++) {

                if (board[i][i] != board[0][0]) {
                    same = false;
                    break;
                }
            }

            if (same) {
                return board[0][0];
            }
        }

        // Other diagonal
        if (board[0][size - 1] != null) {

            boolean same = true;

            for (int i = 1; i < size; i++) {

                if (board[i][size - 1 - i]
                    != board[0][size - 1]) {

                    same = false;
                    break;
                }
            }

            if (same) {
                return board[0][size - 1];
            }
        }

        return null;
    }

    void printBoard() {

        for (int i = 0; i < size; i++) {

            for (int j = 0; j < size; j++) {

                if (board[i][j] == null) {
                    System.out.print("-");
                } else {
                    System.out.print(
                        board[i][j]
                    );
                }

                if (j < size - 1) {
                    System.out.print(" | ");
                }
            }

            System.out.println();
        }
    }
}
```

## Game

```java
class Game {

    Board board;

    Player[] players;

    int currentPlayer = 0;

    Game(Player p1, Player p2) {

        board = new Board(3);

        players = new Player[2];

        players[0] = p1;
        players[1] = p2;
    }

    String play(int row, int col) {

        Player player =
            players[currentPlayer];

        Move move =
            new Move(
                row,
                col,
                player.symbol
            );

        if (!board.placeMove(move)) {

            return "Cell already occupied";
        }

        Symbol winner =
            board.checkWinner();

        if (winner != null) {

            return player.name + " wins!";
        }

        if (board.isFull()) {

            return "Draw!";
        }

        currentPlayer =
            1 - currentPlayer;

        return "Next turn";
    }
}
```

## Usage

```java
public class Main {

    public static void main(String[] args) {

        Player p1 =
            new Player("Alice", Symbol.X);

        Player p2 =
            new Player("Bob", Symbol.O);

        Game game =
            new Game(p1, p2);

        System.out.println(
            game.play(0, 0)
        );

        System.out.println(
            game.play(1, 1)
        );

        System.out.println(
            game.play(0, 1)
        );

        System.out.println(
            game.play(2, 2)
        );

        System.out.println(
            game.play(0, 2)
        );

        game.board.printBoard();
    }
}
```

Output:

```text
Alice's turn → X
Bob's turn   → O
Alice's turn → X
Bob's turn   → O
Alice wins!
```

---

# 6. OOP CONCEPTS USED IN THESE LLD QUESTIONS

When presenting an LLD in an interview, explicitly point out the OOP concepts.

## Inheritance

```java
class User extends Person {
}
```

```text
Person
 ├── User
 └── Librarian
```

---

## Composition

```java
class Car {

    Engine engine;

    Car() {
        engine = new Engine();
    }
}
```

The object owns another object.

Examples from these designs:

```text
ParkingLot → Floors
Floor → ParkingSpots
Elevator → Door
Game → Board
ATM → CashDispenser
```

---

## Association

Objects simply interact.

```java
class ParkingSpot {

    Vehicle vehicle;
}
```

The spot is associated with a vehicle when occupied.

---

## Encapsulation

```java
class Account {

    private double balance;

    public double getBalance() {
        return balance;
    }
}
```

Keep important data private and control how it is changed.

---

## Abstraction

Use an interface when you want interchangeable behavior.

Example:

```java
interface PricingStrategy {

    double calculateFee(Ticket ticket);
}
```

Then:

```java
class NormalPricing implements PricingStrategy {

    public double calculateFee(Ticket ticket) {
        return 20;
    }
}
```

Another implementation could be:

```java
class WeekendPricing implements PricingStrategy {

    public double calculateFee(Ticket ticket) {
        return 30;
    }
}
```

The parking lot doesn't need to know how pricing works.

---

# 7. IMPORTANT DESIGN PATTERNS TO MENTION

You don't need to implement every pattern unless asked.

Know these basic ones:

### Singleton

One object.

Example:

```text
ParkingLot
ElevatorController
```

---

### Strategy

Change an algorithm/behavior without changing the main class.

Examples:

```text
PricingStrategy
DispatchStrategy
SpotAssignmentStrategy
```

---

### State

Object behaves differently depending on its state.

Perfect for:

```text
ATM
Elevator
```

ATM:

```text
Idle
 ↓
Card Inserted
 ↓
Authenticated
 ↓
Transaction
 ↓
Idle
```

---

### Observer

Notify multiple objects when something happens.

Example:

```text
Library
   ↓
Book due
   ↓
NotificationService
   ├── Email
   └── SMS
```

---

# 8. HOW TO APPROACH AN LLD QUESTION IN AN INTERVIEW

If interviewer says:

> "Design a Parking Lot."

Don't immediately start coding.

Follow:

### Step 1 — Requirements

Ask:

> "How many floors?"

> "What vehicle types?"

> "What spot types?"

> "How should pricing work?"

---

### Step 2 — Identify classes

For Parking Lot:

```text
ParkingLot
Floor
ParkingSpot
Vehicle
Ticket
Payment
```

---

### Step 3 — Identify relationships

```text
ParkingLot HAS Floors
Floor HAS ParkingSpots
ParkingSpot HAS Vehicle
Ticket HAS Vehicle + Spot
```

---

### Step 4 — Identify inheritance

```text
Vehicle
 ├── Car
 ├── Motorcycle
 └── Truck
```

---

### Step 5 — Write important methods

```java
parkVehicle()
removeVehicle()
findSpot()
calculateFee()
```

---

### Step 6 — Code the core flow

Don't try to implement every possible feature.

Get this working first:

```text
Vehicle
   ↓
Find Spot
   ↓
Park
   ↓
Generate Ticket
   ↓
Remove Vehicle
   ↓
Calculate Fee
```

---

### Step 7 — Discuss extensibility

Then mention:

> "If requirements change, I would introduce Strategy/State interfaces rather than putting everything into one large class."

That is usually much better than writing 500 lines of code.

---

# 9. WHAT TO MEMORIZE FOR FRESHER LLD

You do **not** need to memorize all these implementations line by line.

Memorize the structure.

## Parking Lot

```text
Vehicle
ParkingSpot
Floor
ParkingLot
Ticket
Payment
```

## Elevator

```text
Request
Door
Elevator
ElevatorController
```

## Library

```text
Person
User
Librarian
Book
BookItem
Loan
Library
```

## ATM

```text
Card
Account
Transaction
CashDispenser
ATM
```

## Tic-Tac-Toe

```text
Player
Move
Board
Game
```

And remember the relationships:

```text
IS-A  → inheritance
HAS-A → composition/aggregation
USES  → association/dependency
```

The goal in a fresher interview is to show that you can take **requirements → classes → relationships → OOP principles → working code**, rather than producing a huge enterprise-level architecture.


**Extensibility talking points:** generalize to NxN board (already done above); support an `AIPlayer` subclass of `Player` implementing minimax — shows polymorphism in action (`Player` as abstract base, `HumanPlayer`/`AIPlayer` as subclasses); add `GameState` (IN_PROGRESS/WON/DRAW) enum instead of ad hoc return strings; add undo via the `moves` history (Memento-pattern flavor).

---

## 5. QUICK-FIRE CHEAT SHEET (for last-minute review)

- **Encapsulation** = bundle + hide → getters/setters, private fields.
- **Abstraction** = hide *how*, expose *what* → abstract class / interface.
- **Abstract class**: can have state + constructor + partial implementation, single inheritance. **Interface**: pure contract, multiple implementation, no state (traditionally).
- **Inheritance** = IS-A, code reuse via hierarchy. **Composition** = HAS-A, code reuse via delegation — prefer composition for flexibility.
- **Association** (uses, no ownership) → **Aggregation** (weak ownership, independent lifecycle) → **Composition** (strong ownership, tied lifecycle) — increasing coupling.
- **Overloading** = same name, different signature, compile-time. **Overriding** = same signature, subclass redefines, runtime (dynamic dispatch via vtable).
- **static** = belongs to class, shared. **final** (Java) = can't reassign / override / subclass.
- **this/self** = current instance. **super** = parent class reference, used for constructor chaining and calling overridden parent methods.
- **Virtual function** = enables runtime polymorphism (C++ needs explicit `virtual`; Java is virtual by default unless `final`/`static`/`private`; Python always dynamic).
- For any **LLD question**: nouns → classes, verbs → methods, then explicitly call out association/aggregation/composition and where you'd inject a **Strategy** or **State** pattern for extensibility — this is usually the differentiator in Coupa-style interviews.
