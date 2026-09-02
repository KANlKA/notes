//Composition is a strong "has-a" relationship where one object owns another object's lifecycle.

class Room {
    private String name;
    public Room(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
class House {
    private List<Room> rooms;
    public House() {
        rooms = new ArrayList<>();
        rooms.add(new Room("Bedroom"));
        rooms.add(new Room("Kitchen"));
        rooms.add(new Room("Living Room"));
    }
    public void showRooms() {
        for (Room room : rooms) {
            System.out.println(room.getName());
        }
    }
}
public class Main {
    public static void main(String[] args) {
        House house = new House();
        house.showRooms();
    }
}
