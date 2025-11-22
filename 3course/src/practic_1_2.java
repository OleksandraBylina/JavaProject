import java.util.Scanner;



public class practic_1_2 {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        int n = input.nextInt();
        int k = input.nextInt();
        int m = n ^ (1 << k);
        System.out.println(m);
    }
}