
import java.util.*;

public class Solution {
    public List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();

        List<Integer> temp=new ArrayList<>();
        if(temp.size()==nums.length) {
            result.add(temp);
        }else{
            for(int num:nums) {
                temp.add(num);
                temp.remove(temp.size()-1);
            }
            result.add(temp);
        }

        return result;
    }


    public static void main(String[] args) {
        Solution solution = new Solution();
        int[] nums = {1, 2, 3};
        List<List<Integer>> permutations = solution.permute(nums);
        System.out.println(permutations);
    }
}