package boys.indecent.kattendance;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.firebase.auth.FirebaseAuth;

public class HomeActivity extends AppCompatActivity {

    Button button, check_attendance, give_attendance;
    FirebaseAuth auth;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        auth = FirebaseAuth.getInstance();

        button = findViewById(R.id.signOutButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        check_attendance=findViewById(R.id.check_attendance);
        give_attendance=findViewById(R.id.give_attendance);

        /*give_attendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(HomeActivity.this,GiveAttendance.class));
            }
        });
        check_attendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(HomeActivity.this,CheckAttendance.class));
            }
        });*/
    }



    @Override
    protected void onStart() {
        super.onStart();
        if (auth.getCurrentUser() == null){
            signOut();
        }
    }

    protected void signOut(){
        auth.signOut();
        Intent intent = new Intent(getApplicationContext(),SignInActivity.class);
        startActivity(intent);
        finish();
    }

    public void startStudentActivity(View view) {
        startActivity(new Intent(HomeActivity.this, NearbyAttendanceActivity.class));
    }

    public void startParentActivity(View view) {
        startActivity(new Intent(HomeActivity.this, DemoTeacher.class));
    }
}
