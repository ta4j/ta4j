package ta4jexamples.strategies;

class Pet{
    public Pet(int age){
        System.out.print("Pet age: " + age);
    }
}
public class Cat extends Pet{
    public Cat(int age){
        super(age);
        System.out.print("Cat");
    }
    public static void main(String[] args) {
        new Pet(5);
    }
}