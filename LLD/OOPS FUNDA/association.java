//Association represents a relationship between two independent objects where they interact or are connected to each other.
class Teacher {
    private String name;
    public Teacher(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    public void teach(Student student) {
        System.out.println(name + " is teaching " + student.getName());
    }
}
class Student {
    private String name;
    public Student(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
public class Main {
    public static void main(String[] args) {
        Teacher teacher = new Teacher("Rahul");
        Student student = new Student("Kanika");
        teacher.teach(student);
    }
}
