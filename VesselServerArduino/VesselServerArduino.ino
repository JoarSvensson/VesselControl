/*
	VesselControlArduino
		Created 2012-10-09
		Rasmus Jansson and Joar Svensson
		Version 0.2
*/

#include <Servo.h>
#include <AFMotor.h>
// Initialize servos
Servo servoX;
Servo servoY;
//AF_DCMotor motor(3);

void setup() 
{
  servoX.attach(9);
  servoY.attach(10);
  
  //motor.setSpeed(200);
  //motor.run(RELEASE);
  
  Serial.begin(9600);
  Serial.println("Arduino Uno available");
}

void loop() 
{ 
    if(Serial.available() >= 3)
    {
      int data = 0;
      data = Serial.read(); 
      if(data == 255)
      {
        int servo = Serial.read();
        int angle = Serial.read();  
        moveServo(servo, angle); 
      }
    }
}

// Method for moving servo
void moveServo(int servo, int angle) 
{
  switch(servo)
  {
    case 1:
      servoX.write(angle);
      break;
    case 2:
      servoY.write(angle);
      //motor.run(FORWARD);
      //motor.setSpeed(angle);
      break;
    default:
      break;
  }  
}
