//Aggregation is a special type of association where one object has a relationship with another object, but both objects can exist independently.

class Employee {
    private String name;
    public Employee(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
class Department {
    private String name;
    private List<Employee> employees;
    public Department(String name, List<Employee> employees) {
        this.name = name;
        this.employees = employees;
    }
    public void showEmployees() {
        System.out.println("Department: " + name);
        for (Employee employee : employees) {
            System.out.println(employee.getName());
        }
    }
}
public class Main {
    public static void main(String[] args) {
        Employee employee1 = new Employee("Kanika");
        Employee employee2 = new Employee("Rahul");
        List<Employee> employees = List.of(employee1, employee2);
        Department department = new Department("Engineering", employees);
        department.showEmployees();
    }
}
