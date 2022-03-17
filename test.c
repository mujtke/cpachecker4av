typedef unsigned long pthread_t;

extern void reach_error();

int x = -1, y = 2;

void t1(void *arg)
{
	y = 1;
	x = x + 1;
}

void t2(void *arg)
{
	x = x + 1;
	if(x <= y - 2) {
		y = x + 1;
	}
}

int main()
{
	pthread_t tid1, tid2;
	
	pthread_create(&tid1, 0, t1, 0);
	pthread_create(&tid2, 0, t2, 0);

	pthread_join(tid1, 0);
	pthread_join(tid2, 0);
	
	if(!(y < 2)) {
		reach_error();
	}
	
	return 0;
}
