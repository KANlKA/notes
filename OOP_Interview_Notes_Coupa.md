# OOP Interview Notes — Coupa Prep

> Code examples use **Python** for readability, with **Java** snippets wherever a concept (overloading, strict access modifiers, `final`, interfaces) is easier to show in a statically-typed language. Both are commonly accepted in Coupa interviews — know the concept, not just one syntax.

---

## 1. OOP FUNDAMENTALS

### 1.1 Class & Object

A **class** is a blueprint/template. An **object** is a runtime instance of that class, occupying memory.

```python
class Car:
    def __init__(self, brand, speed):
        self.brand = brand      # instance variable
        self.speed = speed

    def accelerate(self, amount):   # method
        self.speed += amount
        return self.speed

car1 = Car("Toyota", 0)   # object 1
car2 = Car("Honda", 0)    # object 2 — independent state
car1.accelerate(20)
print(car1.speed, car2.speed)   # 20 0
```

**Interview line:** *"A class defines structure and behavior; an object is a concrete instantiation with its own state in memory."*

### 1.2 Constructor

Special method invoked automatically when an object is created. Used to initialize state.

```python
class Point:
    def __init__(self, x=0, y=0):   # constructor
        self.x = x
        self.y = y
```

```java
public class Point {
    private int x, y;
    public Point() { this(0, 0); }         // default constructor
    public Point(int x, int y) {           // parameterized constructor
        this.x = x;
        this.y = y;
    }
}
```

- **Default constructor** — no args, auto-provided by compiler if none defined (Java/C++).
- **Parameterized constructor** — accepts args to set initial state.
- **Copy constructor** (C++) — builds an object from another object of the same class.

### 1.3 Destructor — language dependent

- **C++**: `~ClassName()` — called deterministically when object goes out of scope / `delete`d. Used for manual resource cleanup (RAII).
- **Java/Python**: No deterministic destructor. Garbage collector reclaims memory.
  - Python has `__del__` (finalizer) — but timing isn't guaranteed; not reliable for critical cleanup.
  - Java has no destructor; `finalize()` is deprecated. Use `try-with-resources` / `AutoCloseable`.
  - Python idiom: use context managers (`with` + `__enter__`/`__exit__`) instead of relying on `__del__`.

```cpp
class FileHandler {
public:
    FileHandler() { /* open file */ }
    ~FileHandler() { /* close file - deterministic */ }
};
```

```python
class FileHandler:
    def __enter__(self):
        self.f = open("data.txt")
        return self.f
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.f.close()   # deterministic cleanup, Pythonic replacement for destructor
```

**Interview line:** *"C++ has deterministic destructors tied to scope (RAII). Java/Python rely on GC, so cleanup of external resources (files, sockets, DB connections) is done via explicit patterns — try-with-resources in Java, context managers in Python — not destructors."*

### 1.4 Instance Variables vs Class (Static) Variables

```python
class Employee:
    company = "Coupa"          # class variable — shared across all instances

    def __init__(self, name, salary):
        self.name = name       # instance variable — unique per object
        self.salary = salary

e1 = Employee("Alice", 90000)
e2 = Employee("Bob", 85000)
Employee.company = "Coupa Inc"   # affects all instances
print(e1.company, e2.company)    # Coupa Inc, Coupa Inc
```

### 1.5 Methods

- **Instance method** — operates on `self`/`this`, can access instance state.
- **Class method** (`@classmethod` / `static` factory) — operates on the class, not instance.
- **Static method** — utility function, no access to instance or class state.

```python
class MathUtils:
    @staticmethod
    def square(n):
        return n * n

    @classmethod
    def from_string(cls, s):
        return cls(int(s))
```


---

## 2. THE FOUR PILLARS OF OOP

### 2.1 Encapsulation

Bundling data + methods that operate on it into a single unit, and **restricting direct access** to internal state ("data hiding").

```python
class BankAccount:
    def __init__(self, balance):
        self.__balance = balance     # name-mangled -> "private"

    def deposit(self, amount):
        if amount <= 0:
            raise ValueError("amount must be positive")
        self.__balance += amount

    def get_balance(self):           # controlled access
        return self.__balance

acc = BankAccount(100)
acc.deposit(50)
print(acc.get_balance())    # 150
# acc.__balance            # AttributeError-ish (name mangled) -> can't access directly
```

```java
public class BankAccount {
    private double balance;      // hidden
    public void deposit(double amt) {
        if (amt <= 0) throw new IllegalArgumentException();
        balance += amt;
    }
    public double getBalance() { return balance; }   // getter — controlled access
}
```

**Data hiding vs Encapsulation:**
- Encapsulation = bundling data + behavior together (a broader OOP concept).
- Data hiding = the *access-restriction* mechanism (private fields + getters/setters) that enforces encapsulation. Data hiding is a technique that helps achieve encapsulation.

**Access Modifiers**

| Modifier | Java | Python (convention) | Meaning |
|---|---|---|---|
| public | `public` | `name` | accessible everywhere |
| protected | `protected` | `_name` (single underscore, convention only) | accessible in class + subclasses (+ package in Java) |
| private | `private` | `__name` (name-mangled, not true private) | accessible only within class |
| package-private | (default, no modifier) | N/A | accessible within same package (Java only) |

Python has **no true enforced access modifiers** — everything is accessible; underscores are conventions (`_protected`, `__private` triggers name mangling to `_ClassName__private`, mainly to avoid subclass name clashes, not real security).

### 2.2 Abstraction

Exposing only relevant behavior, hiding implementation detail. Achieved via **abstract classes** and **interfaces**.

```python
from abc import ABC, abstractmethod

class Shape(ABC):                      # abstract class
    @abstractmethod
    def area(self):                    # abstract method — no implementation
        pass

    def describe(self):                # concrete method — shared implementation
        return f"This shape has area {self.area()}"

class Circle(Shape):
    def __init__(self, r):
        self.r = r
    def area(self):
        return 3.14159 * self.r ** 2

# Shape()          -> TypeError: Can't instantiate abstract class
c = Circle(5)
print(c.describe())
```

```java
// Abstract class
abstract class Shape {
    abstract double area();                 // no body
    void describe() {                       // concrete method allowed
        System.out.println("Area: " + area());
    }
}

// Interface
interface Drawable {
    void draw();                            // implicitly public abstract
}

class Circle extends Shape implements Drawable {
    double r;
    Circle(double r) { this.r = r; }
    double area() { return Math.PI * r * r; }
    public void draw() { System.out.println("Drawing circle"); }
}
```

### 2.3 Inheritance

A class (child/derived) acquires properties & behavior of another (parent/base).

```python
class Animal:
    def __init__(self, name):
        self.name = name
    def speak(self):
        return "..."

class Dog(Animal):                 # single inheritance
    def speak(self):
        return f"{self.name} says Woof"

class Puppy(Dog):                  # multilevel inheritance (Animal -> Dog -> Puppy)
    def speak(self):
        return f"{self.name} yips"

class Cat(Animal):
    def speak(self):
        return f"{self.name} says Meow"
# Animal -> Dog, Cat  is HIERARCHICAL inheritance (one parent, many children)
```

**Types:**
- **Single** — one base, one derived (`Dog extends Animal`)
- **Multilevel** — chain: `Animal -> Dog -> Puppy`
- **Hierarchical** — one base, multiple derived classes (`Dog`, `Cat` both extend `Animal`)
- **Multiple inheritance — language dependent**
  - Python: supported directly (uses **MRO** — Method Resolution Order / C3 linearization to resolve conflicts).
  - Java/C#: NOT supported for classes (avoids the "Diamond Problem"); achieved via **multiple interface implementation** instead.
  - C++: supported directly, but you must manually resolve ambiguity (virtual inheritance to solve diamond problem).

```python
class Flyer:
    def move(self): return "flying"
class Swimmer:
    def move(self): return "swimming"
class Duck(Flyer, Swimmer):      # multiple inheritance
    pass
print(Duck().move())    # "flying" -> resolved via MRO (left-to-right)
print(Duck.__mro__)
```

```cpp
// Diamond problem in C++
class A { public: void hello() { cout << "A"; } };
class B : public A {};
class C : public A {};
class D : public B, public C {};   // ambiguous: D has two copies of A
// Fix: class B : virtual public A {}; class C : virtual public A {};
```

### 2.4 Polymorphism

"Many forms" — same interface, different underlying implementation.

**Compile-time (static) polymorphism — Method Overloading**
Same method name, different signature (params), resolved at compile time. Python doesn't support true overloading (last definition wins); simulate with default args / `*args` / `functools.singledispatch`.

```java
class Calculator {
    int add(int a, int b) { return a + b; }
    double add(double a, double b) { return a + b; }      // overload: different types
    int add(int a, int b, int c) { return a + b + c; }    // overload: different arity
}
```

```python
from functools import singledispatch

@singledispatch
def add(a, b):
    return a + b   # simulated overloading in Python
```

**Runtime (dynamic) polymorphism — Method Overriding**
Subclass redefines a method from the parent with the *same signature*; resolved at runtime via **dynamic dispatch** (vtable lookup).

```python
class Shape:
    def area(self):
        return 0

class Rectangle(Shape):
    def __init__(self, w, h):
        self.w, self.h = w, h
    def area(self):                 # override
        return self.w * self.h

class Circle(Shape):
    def __init__(self, r):
        self.r = r
    def area(self):                 # override
        return 3.14159 * self.r ** 2

shapes = [Rectangle(3, 4), Circle(5)]
for s in shapes:
    print(s.area())    # calls the correct overridden method at runtime
```

```java
Shape s = new Circle(5);   // reference type Shape, actual object Circle
s.area();                  // JVM resolves to Circle.area() at RUNTIME (dynamic dispatch via vtable)
```

**Overloading vs Overriding — quick table**

| | Overloading | Overriding |
|---|---|---|
| Binding | Compile-time (static) | Runtime (dynamic) |
| Where | Same class (or subclass with covariant use) | Parent–child relationship |
| Signature | Must differ (params/types/arity) | Must be identical |
| Return type | Can differ | Must be same or covariant |
| Purpose | Convenience / same operation on different inputs | Specialize/replace inherited behavior |


---

## 3. DEEP OOP — MUST KNOW COLD

### 3.1 Abstract Class vs Interface

| | Abstract Class | Interface |
|---|---|---|
| Methods | Can have abstract + concrete methods | Traditionally all abstract (Java 8+ allows `default`/`static` methods) |
| State | Can have instance variables/state | No instance state (only constants, `public static final`) |
| Constructor | Can have a constructor | No constructor |
| Inheritance | Single inheritance (`extends` one class) | A class can implement **multiple** interfaces |
| Access modifiers | Any (`public`/`protected`/`private` members) | Members implicitly `public` |
| When to use | "IS-A" relationship + shared code/state to reuse | "CAN-DO" / capability contract, especially across unrelated classes |

```java
interface Payable { double calculatePay(); }
interface Taxable { double calculateTax(); }

abstract class Employee implements Payable, Taxable {   // abstract class + multiple interfaces
    protected String name;
    Employee(String name) { this.name = name; }         // abstract classes CAN have constructors
    abstract double calculatePay();                       // still abstract
    double calculateTax() { return calculatePay() * 0.2; } // shared concrete implementation
}
```

**Interview soundbite:** *"Use an abstract class when subclasses share common state/behavior and there's a true IS-A hierarchy. Use an interface when you need a contract that unrelated classes can implement — Java doesn't allow multiple class inheritance, so interfaces are how you get multiple-inheritance-like behavior."*

### 3.2 Composition vs Inheritance ("favor composition over inheritance")

- **Inheritance (IS-A)** — tight coupling, subclass depends on parent's implementation, can break with parent changes (fragile base class problem).
- **Composition (HAS-A)** — object contains references to other objects and delegates work to them; more flexible, swappable at runtime, avoids deep/fragile hierarchies.

```python
# Inheritance approach - rigid
class Engine:
    def start(self): return "Engine starting"

class Car(Engine):     # WRONG - Car IS-NOT-A Engine
    pass

# Composition approach - correct
class Car:
    def __init__(self, engine):
        self.engine = engine     # Car HAS-A Engine
    def start(self):
        return self.engine.start()

petrol_engine = Engine()
car = Car(petrol_engine)
print(car.start())
```

Strategy pattern is a classic example of composition beating inheritance: instead of subclassing for every behavior variant, inject a behavior object.

### 3.3 Association, Aggregation, Composition (UML relationship strength)

All three describe **object relationships** ("HAS-A" family), differing in **ownership** and **lifecycle**:

1. **Association** — general relationship; objects know about each other, no ownership. Can be 1:1, 1:many, many:many. Independent lifecycles.
   ```python
   class Teacher:
       pass
   class Student:
       def __init__(self, teacher):
           self.teacher = teacher   # association: Student "uses" a Teacher, no ownership
   ```

2. **Aggregation** — "weak HAS-A". A whole contains parts, but parts can exist independently and can outlive the whole / be shared across wholes.
   ```python
   class Department:
       def __init__(self):
           self.professors = []       # Department HAS Professors
       def add(self, prof):
           self.professors.append(prof)

   class Professor:
       def __init__(self, name):
           self.name = name

   p = Professor("Dr. Rao")
   dept = Department()
   dept.add(p)
   # if dept is deleted, Professor p still exists independently (could join another dept)
   ```

3. **Composition** — "strong HAS-A". Whole *owns* the parts; parts' lifecycle is bound to the whole — when the whole is destroyed, parts are destroyed too.
   ```python
   class Heart:
       def beat(self): return "thump"

   class Human:
       def __init__(self):
           self.heart = Heart()    # Heart created and destroyed with Human — no Human, no this Heart
   ```

**Strength ranking:** Association (weakest) → Aggregation → Composition (strongest, ownership + shared lifecycle).

### 3.4 static

Belongs to the **class**, not any instance. Shared across all objects, exists without instantiation.

```java
class Counter {
    static int count = 0;             // one copy shared by all instances
    Counter() { count++; }
    static int getCount() { return count; }   // static method: can't use `this`
}
new Counter(); new Counter();
System.out.println(Counter.getCount());  // 2
```

### 3.5 final (Java) / const-ness concepts

- `final` **variable** — value can't be reassigned after initialization (constant).
- `final` **method** — cannot be overridden by subclasses.
- `final` **class** — cannot be subclassed at all (e.g. `String`, `Integer` in Java).

```java
final class ImmutablePoint {              // can't be extended
    private final int x, y;               // can't be reassigned after constructor
    ImmutablePoint(int x, int y) { this.x = x; this.y = y; }
    final int getX() { return x; }        // can't be overridden (redundant here since class is final)
}
```

Python has no true `final` (convention: `Final[int]` type hint via `typing`, not enforced at runtime).

### 3.6 Virtual Functions & Dynamic Dispatch

- **Virtual function** (C++ term) — a member function you expect to be overridden; declared with `virtual` so the call is resolved via the object's **actual type at runtime**, not the pointer/reference's declared type.
- Without `virtual`, C++ uses **static binding** (resolved at compile time based on declared type) — this is a classic interview gotcha.

```cpp
class Animal {
public:
    virtual void speak() { cout << "..."; }     // virtual -> dynamic dispatch
    void nonVirtual() { cout << "base"; }        // NOT virtual -> static binding
};
class Dog : public Animal {
public:
    void speak() override { cout << "Woof"; }
    void nonVirtual() { cout << "derived"; }
};

Animal* a = new Dog();
a->speak();       // "Woof" -> virtual, dynamic dispatch based on actual object type
a->nonVirtual();  // "base" -> NOT virtual, static binding based on pointer type Animal*
```

- Java: **all non-static, non-final, non-private methods are virtual by default** — always dynamic dispatch.
- Python: everything is dynamically dispatched by default (duck typing), no `virtual` keyword needed.

**Mechanism:** implemented via a **vtable** (virtual method table) — each object with virtual methods carries a pointer to a table of function pointers; the runtime looks up the correct implementation through that table based on the object's real class.

### 3.7 Constructor Chaining

Calling one constructor from another — either within the same class (overloaded constructors) or from a subclass to its parent — to avoid duplicated init logic.

```java
class Vehicle {
    String brand; int wheels;
    Vehicle(String brand) { this(brand, 4); }              // chain to another ctor in same class via this()
    Vehicle(String brand, int wheels) {
        this.brand = brand; this.wheels = wheels;
    }
}
class Car extends Vehicle {
    String model;
    Car(String brand, String model) {
        super(brand);          // chain to parent constructor via super()
        this.model = model;
    }
}
```

```python
class Vehicle:
    def __init__(self, brand, wheels=4):
        self.brand = brand
        self.wheels = wheels

class Car(Vehicle):
    def __init__(self, brand, model):
        super().__init__(brand)      # constructor chaining to parent via super()
        self.model = model
```

### 3.8 `this` / `self` vs `super`

- `this` (Java/C++) / `self` (Python, explicit first param) — reference to the **current object instance**; disambiguates instance fields from parameters, used to call other constructors (`this(...)`) or pass current object around.
- `super` — reference to the **parent class**; used to call parent's constructor (`super(...)`) or explicitly invoke an overridden parent method (`super.method()` / `super().method()`).

```python
class Animal:
    def __init__(self, name):
        self.name = name
    def speak(self):
        return "generic sound"

class Dog(Animal):
    def __init__(self, name, breed):
        super().__init__(name)          # call parent constructor
        self.breed = breed
    def speak(self):
        parent_sound = super().speak()  # call parent's overridden method explicitly
        return f"{parent_sound}, but really: Woof"
```


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
