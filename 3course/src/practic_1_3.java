import java.util.Scanner;



public class practic_1_3 {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        int n = input.nextInt();
        int n1 = 1;
        int n2 = 1;
        int n3 = 2;
        int fib;
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            fib = n1 + n2;
            sum += fib;
            n1 = n2;
            n2 = n3;
            n3 = fib;
        }
        System.out.println(sum);
    }
}
