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

### 4.1 Parking Lot

**Requirements (typical):** multiple floors, multiple spot types (compact/large/handicapped/motorcycle), entry generates a ticket, exit calculates payment, track availability.

**Relationships:**
- `ParkingLot` **has-many** `Floor` (composition — floors don't exist without the lot)
- `Floor` **has-many** `ParkingSpot` (composition)
- `ParkingSpot` **associated with** `Vehicle` when occupied (temporary association, not ownership)
- `Ticket` **associates** a `Vehicle` with a `ParkingSpot` and entry time
- `Payment` **associates** with a `Ticket`
- `Vehicle` — base class, subclasses `Car`, `Motorcycle`, `Truck` (inheritance)
- `ParkingSpot` — abstract-ish base with `SpotType` enum, or subclassed per type

```python
from abc import ABC
from enum import Enum
from datetime import datetime
import uuid

class VehicleType(Enum):
    MOTORCYCLE = 1
    CAR = 2
    TRUCK = 3

class SpotType(Enum):
    MOTORCYCLE = 1
    COMPACT = 2
    LARGE = 3
    HANDICAPPED = 4

class Vehicle(ABC):
    def __init__(self, license_plate, vtype: VehicleType):
        self.license_plate = license_plate
        self.type = vtype

class Car(Vehicle):
    def __init__(self, plate): super().__init__(plate, VehicleType.CAR)

class Motorcycle(Vehicle):
    def __init__(self, plate): super().__init__(plate, VehicleType.MOTORCYCLE)

class Truck(Vehicle):
    def __init__(self, plate): super().__init__(plate, VehicleType.TRUCK)


class ParkingSpot:
    def __init__(self, spot_id, spot_type: SpotType):
        self.spot_id = spot_id
        self.spot_type = spot_type
        self.vehicle = None          # association when occupied

    def is_free(self):
        return self.vehicle is None

    def can_fit(self, vehicle: Vehicle):
        compat = {
            VehicleType.MOTORCYCLE: {SpotType.MOTORCYCLE, SpotType.COMPACT, SpotType.LARGE},
            VehicleType.CAR: {SpotType.COMPACT, SpotType.LARGE},
            VehicleType.TRUCK: {SpotType.LARGE},
        }
        return self.spot_type in compat[vehicle.type]

    def assign(self, vehicle):
        self.vehicle = vehicle

    def remove(self):
        self.vehicle = None


class Floor:
    def __init__(self, floor_num, spots):
        self.floor_num = floor_num
        self.spots = spots           # composition: Floor owns these ParkingSpot objects

    def find_available_spot(self, vehicle):
        for spot in self.spots:
            if spot.is_free() and spot.can_fit(vehicle):
                return spot
        return None


class Ticket:
    def __init__(self, vehicle, spot):
        self.ticket_id = str(uuid.uuid4())
        self.vehicle = vehicle
        self.spot = spot
        self.entry_time = datetime.now()
        self.exit_time = None


class Payment:
    RATE_PER_HOUR = 2.0

    @staticmethod
    def calculate(ticket: Ticket):
        duration_hrs = max(1, (ticket.exit_time - ticket.entry_time).seconds // 3600)
        return duration_hrs * Payment.RATE_PER_HOUR


class ParkingLot:                     # Singleton in practice — only one lot
    def __init__(self, floors):
        self.floors = floors          # composition: ParkingLot owns Floors
        self.active_tickets = {}      # ticket_id -> Ticket

    def park_vehicle(self, vehicle) -> Ticket:
        for floor in self.floors:
            spot = floor.find_available_spot(vehicle)
            if spot:
                spot.assign(vehicle)
                ticket = Ticket(vehicle, spot)
                self.active_tickets[ticket.ticket_id] = ticket
                return ticket
        raise Exception("Parking lot full for this vehicle type")

    def unpark_vehicle(self, ticket_id):
        ticket = self.active_tickets.pop(ticket_id)
        ticket.exit_time = datetime.now()
        amount = Payment.calculate(ticket)
        ticket.spot.remove()
        return amount


# --- usage ---
lot = ParkingLot([Floor(1, [ParkingSpot(f"1-{i}", SpotType.COMPACT) for i in range(5)])])
car = Car("KA-01-AB-1234")
t = lot.park_vehicle(car)
print("Ticket:", t.ticket_id)
print("Fee:", lot.unpark_vehicle(t.ticket_id))
```

**Extensibility talking points:** add a new `SpotType`/`VehicleType` without touching core logic (Open/Closed); swap `Payment` for a `PricingStrategy` interface to support surge pricing / membership discounts (Strategy pattern); make `ParkingLot` a Singleton; add `SpotAssignmentStrategy` (nearest-first vs random) as a pluggable strategy.

---

### 4.2 Elevator System

**Requirements:** multiple elevators, multiple floors, users request pickup (external button) and destination (internal button), a controller dispatches the optimal elevator.

**Relationships:**
- `ElevatorController` **manages** many `Elevator` (association/aggregation — controller doesn't "own" lifecycle, just coordinates)
- `Elevator` **has-a** `Door` (composition)
- `Elevator` **serves** `Floor`s and processes `Request`s (association)
- `Request` — encapsulates a floor + direction (or destination floor)

```python
from enum import Enum
from collections import deque

class Direction(Enum):
    UP = 1
    DOWN = 2
    IDLE = 3

class DoorState(Enum):
    OPEN = 1
    CLOSED = 2

class Door:
    def __init__(self):
        self.state = DoorState.CLOSED
    def open(self): self.state = DoorState.OPEN
    def close(self): self.state = DoorState.CLOSED

class Request:
    def __init__(self, floor, direction=None):
        self.floor = floor
        self.direction = direction     # None for internal "go to floor X" requests

class Elevator:
    def __init__(self, elevator_id, num_floors):
        self.id = elevator_id
        self.current_floor = 1
        self.direction = Direction.IDLE
        self.door = Door()
        self.requests = deque()        # pending stops, could be a sorted structure (SCAN algorithm)

    def add_request(self, request: Request):
        self.requests.append(request)

    def step(self):
        """Move one floor towards the next requested stop (simplified FCFS; real systems use SCAN/LOOK)."""
        if not self.requests:
            self.direction = Direction.IDLE
            return
        target = self.requests[0].floor
        if target > self.current_floor:
            self.direction = Direction.UP
            self.current_floor += 1
        elif target < self.current_floor:
            self.direction = Direction.DOWN
            self.current_floor -= 1
        if self.current_floor == target:
            self.door.open()
            self.requests.popleft()
            self.door.close()

class ElevatorController:
    def __init__(self, num_elevators, num_floors):
        self.elevators = [Elevator(i, num_floors) for i in range(num_elevators)]

    def request_elevator(self, floor, direction):
        """Dispatch algorithm: pick the nearest idle/same-direction elevator."""
        best = min(
            self.elevators,
            key=lambda e: abs(e.current_floor - floor)
        )
        best.add_request(Request(floor, direction))
        return best.id

    def select_floor(self, elevator_id, floor):
        self.elevators[elevator_id].add_request(Request(floor))


# --- usage ---
controller = ElevatorController(num_elevators=3, num_floors=10)
eid = controller.request_elevator(floor=5, direction=Direction.UP)
controller.select_floor(eid, floor=9)
controller.elevators[eid].step()
```

**Extensibility talking points:** replace naive dispatch with a `DispatchStrategy` interface (nearest-car, zoning, destination-dispatch); replace FCFS stop order with SCAN/LOOK algorithm; add `Request` subclasses for maintenance mode / emergency override; make `ElevatorController` a Singleton per building.

---

### 4.3 Library Management System

**Requirements:** catalog of books (possibly multiple copies), users can search/borrow/return, librarians manage inventory and can issue/return on behalf of users, track due dates and fines.

**Relationships:**
- `Library` **has-many** `Book` (aggregation — books could be transferred between library branches conceptually)
- `Book` (title-level) **has-many** `BookItem` (physical copies) — common LLD nuance interviewers like to see
- `User`/`Librarian` — inheritance from a common `Person` (or `Account`) base
- `Issue`/`Return` — record association between a `BookItem` and a `User`, could be modeled as one `Loan`/`Transaction` class

```python
from datetime import datetime, timedelta
from enum import Enum

class BookStatus(Enum):
    AVAILABLE = 1
    LOANED = 2
    LOST = 3

class Person:                                 # base class
    def __init__(self, name, person_id):
        self.name = name
        self.id = person_id

class User(Person):                           # inheritance
    def __init__(self, name, person_id):
        super().__init__(name, person_id)
        self.borrowed_items = []               # association to BookItem(s)

class Librarian(Person):                       # inheritance
    def issue_book(self, library, book_item, user):
        return library.issue_book(book_item, user)

    def return_book(self, library, book_item):
        return library.return_book(book_item)


class Book:                                    # title-level metadata
    def __init__(self, isbn, title, author):
        self.isbn = isbn
        self.title = title
        self.author = author


class BookItem:                                # a physical copy (association to Book)
    def __init__(self, barcode, book: Book):
        self.barcode = barcode
        self.book = book
        self.status = BookStatus.AVAILABLE
        self.due_date = None


class Loan:                                    # models Issue + Return together
    FINE_PER_DAY = 5

    def __init__(self, book_item: BookItem, user: User):
        self.book_item = book_item
        self.user = user
        self.issue_date = datetime.now()
        self.due_date = self.issue_date + timedelta(days=14)
        self.return_date = None

    def calculate_fine(self):
        if self.return_date and self.return_date > self.due_date:
            days_late = (self.return_date - self.due_date).days
            return days_late * Loan.FINE_PER_DAY
        return 0


class Library:
    def __init__(self):
        self.catalog = {}          # isbn -> Book
        self.items = []            # all BookItem copies
        self.active_loans = {}     # barcode -> Loan

    def add_book(self, book: Book, num_copies=1):
        self.catalog[book.isbn] = book
        for i in range(num_copies):
            self.items.append(BookItem(f"{book.isbn}-{i}", book))

    def search_by_title(self, title):
        return [b for b in self.catalog.values() if title.lower() in b.title.lower()]

    def issue_book(self, book_item: BookItem, user: User):
        if book_item.status != BookStatus.AVAILABLE:
            raise Exception("Book not available")
        loan = Loan(book_item, user)
        book_item.status = BookStatus.LOANED
        book_item.due_date = loan.due_date
        user.borrowed_items.append(book_item)
        self.active_loans[book_item.barcode] = loan
        return loan

    def return_book(self, book_item: BookItem):
        loan = self.active_loans.pop(book_item.barcode)
        loan.return_date = datetime.now()
        book_item.status = BookStatus.AVAILABLE
        book_item.due_date = None
        loan.user.borrowed_items.remove(book_item)
        return loan.calculate_fine()


# --- usage ---
library = Library()
b = Book("978-1", "Clean Code", "Robert Martin")
library.add_book(b, num_copies=2)
alice = User("Alice", "U1")
librarian = Librarian("Mr. Rao", "L1")

item = library.items[0]
loan = librarian.issue_book(library, item, alice)
fine = librarian.return_book(library, item)
print("Fine due:", fine)
```

**Extensibility talking points:** separate `Book` (title/metadata) from `BookItem` (physical copy) — a classic detail interviewers probe for; add `Reservation`/hold-queue feature; add `NotificationService` (observer pattern) for due-date reminders; support e-books via a `Media`/`Item` interface that both `BookItem` and `EBookItem` implement.

---

### 4.4 ATM System

**Requirements:** user inserts card + PIN, selects account, withdraws/deposits/checks balance, cash dispenser tracks note denominations, generates a transaction record.

**Relationships:**
- `ATM` **has-a** `CashDispenser` (composition)
- `ATM` **operates on** `Account` via `Card`+authentication (association)
- `Account` **has-many** `Transaction` (composition — transactions belong to that account's history)
- `ATM` uses a **State pattern** internally (Idle → HasCard → Authenticated → Transaction → Dispensing) — good to mention

```python
from enum import Enum

class TransactionType(Enum):
    WITHDRAW = 1
    DEPOSIT = 2
    BALANCE_INQUIRY = 3

class Card:
    def __init__(self, card_number, pin, account_number):
        self.card_number = card_number
        self.__pin = pin                 # encapsulated
        self.account_number = account_number

    def validate_pin(self, entered_pin):
        return self.__pin == entered_pin


class Transaction:
    def __init__(self, ttype: TransactionType, amount, balance_after):
        self.type = ttype
        self.amount = amount
        self.balance_after = balance_after


class Account:
    def __init__(self, account_number, balance=0):
        self.account_number = account_number
        self.balance = balance
        self.transactions = []            # composition: history belongs to account

    def withdraw(self, amount):
        if amount > self.balance:
            raise Exception("Insufficient funds")
        self.balance -= amount
        self.transactions.append(Transaction(TransactionType.WITHDRAW, amount, self.balance))

    def deposit(self, amount):
        self.balance += amount
        self.transactions.append(Transaction(TransactionType.DEPOSIT, amount, self.balance))


class CashDispenser:
    def __init__(self):
        self.denominations = {2000: 0, 500: 20, 200: 20, 100: 50}   # note -> count

    def can_dispense(self, amount):
        return amount % 100 == 0     # simplification

    def dispense(self, amount):
        notes_given = {}
        remaining = amount
        for note in sorted(self.denominations, reverse=True):
            count_needed = min(remaining // note, self.denominations[note])
            if count_needed:
                notes_given[note] = count_needed
                remaining -= note * count_needed
                self.denominations[note] -= count_needed
        if remaining != 0:
            raise Exception("Cannot dispense exact amount with available notes")
        return notes_given


class ATM:
    def __init__(self):
        self.cash_dispenser = CashDispenser()      # composition
        self.accounts = {}                          # account_number -> Account (bank backend, simplified)
        self.authenticated_account = None

    def register_account(self, account: Account):
        self.accounts[account.account_number] = account

    def insert_card(self, card: Card, pin):
        if not card.validate_pin(pin):
            raise Exception("Invalid PIN")
        self.authenticated_account = self.accounts[card.account_number]
        return True

    def withdraw(self, amount):
        acc = self.authenticated_account
        if not self.cash_dispenser.can_dispense(amount):
            raise Exception("Invalid amount")
        acc.withdraw(amount)              # updates account first (or use a transaction/lock in real system)
        return self.cash_dispenser.dispense(amount)

    def check_balance(self):
        return self.authenticated_account.balance

    def eject_card(self):
        self.authenticated_account = None


# --- usage ---
atm = ATM()
acc = Account("ACC1", balance=10000)
atm.register_account(acc)
card = Card("1234-5678", pin="4321", account_number="ACC1")

atm.insert_card(card, "4321")
notes = atm.withdraw(2300)
print("Dispensed:", notes)
print("Balance:", atm.check_balance())
atm.eject_card()
```

**Extensibility talking points:** model the ATM's flow explicitly with a **State design pattern** (`IdleState`, `HasCardState`, `SelectOperationState`, `TransactionState`) instead of if/else flags — interviewers love seeing this; separate `Bank`(backend) from `ATM`(client, only has a `CashDispenser` + network call to Bank) for realism; add `TransactionStrategy` for withdraw/deposit/transfer as pluggable operations (Strategy/Command pattern), enabling easy addition of new operation types.

---

### 4.5 Tic Tac Toe

**Requirements:** 2 players, 3x3 board (or NxN), alternate turns, detect win/draw, replay-able.

**Relationships:**
- `Game` **has-a** `Board` (composition)
- `Game` **has-many** `Player` (composition/aggregation, 2 players)
- `Game` **records** `Move`s (composition — a move history belongs to that game instance)
- `Player` **has-a** `Symbol` (X/O) — could be an enum

```python
from enum import Enum

class Symbol(Enum):
    X = "X"
    O = "O"
    EMPTY = " "

class Player:
    def __init__(self, name, symbol: Symbol):
        self.name = name
        self.symbol = symbol

class Move:
    def __init__(self, row, col, symbol: Symbol):
        self.row = row
        self.col = col
        self.symbol = symbol

class Board:
    def __init__(self, size=3):
        self.size = size
        self.grid = [[Symbol.EMPTY for _ in range(size)] for _ in range(size)]

    def place(self, move: Move):
        if self.grid[move.row][move.col] != Symbol.EMPTY:
            raise Exception("Cell already occupied")
        self.grid[move.row][move.col] = move.symbol

    def is_full(self):
        return all(cell != Symbol.EMPTY for row in self.grid for cell in row)

    def check_winner(self):
        lines = []
        lines.extend(self.grid)                                          # rows
        lines.extend([list(col) for col in zip(*self.grid)])              # cols
        lines.append([self.grid[i][i] for i in range(self.size)])         # main diagonal
        lines.append([self.grid[i][self.size - 1 - i] for i in range(self.size)])  # anti-diagonal

        for line in lines:
            if line[0] != Symbol.EMPTY and all(cell == line[0] for cell in line):
                return line[0]
        return None

    def print_board(self):
        for row in self.grid:
            print(" | ".join(cell.value for cell in row))


class Game:
    def __init__(self, player1: Player, player2: Player, size=3):
        self.board = Board(size)             # composition
        self.players = [player1, player2]    # composition
        self.moves = []                      # composition: move history
        self.current_player_idx = 0
        self.winner = None

    def play_move(self, row, col):
        player = self.players[self.current_player_idx]
        move = Move(row, col, player.symbol)
        self.board.place(move)
        self.moves.append(move)

        result = self.board.check_winner()
        if result:
            self.winner = player
            return f"{player.name} wins!"
        if self.board.is_full():
            return "It's a draw!"

        self.current_player_idx = 1 - self.current_player_idx   # switch turn
        return "Next turn"


# --- usage ---
p1 = Player("Alice", Symbol.X)
p2 = Player("Bob", Symbol.O)
game = Game(p1, p2)

print(game.play_move(0, 0))   # X
print(game.play_move(1, 1))   # O
print(game.play_move(0, 1))   # X
print(game.play_move(2, 2))   # O
print(game.play_move(0, 2))   # X wins (top row)
game.board.print_board()
```

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
