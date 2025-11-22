import java.util.Scanner;

public class practic_1_1 {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        int n = input.nextInt();
        int k = input.nextInt();
        int[] l = new int[n];
        for (int i = 0; i < n; i++) {
            l[i] = input.nextInt();
        }
        int counter = 0;
        for (int i = 0; i < n; i++) {
            if (l[i] % k == 0){
                counter++;
            }
        }
        System.out.println(counter);
    }
}