typedef unsigned long pthread_t;

int x = 0;

void t1(void* arg)
{
	int l1 = x + 1;
	x = l1 + x;
}

void t2(void* arg)
{
	int l2 = x + 2;
	x = l2 + x;
}

int main()
{
	pthread_t tid1, tid2;
	
	pthread_create(&tid1, (void*)0, t1, (void*)0);
	pthread_create(&tid2, (void*)0, t2, (void*)0);
	
	pthread_join(tid1, (void*)0);
	pthread_join(tid2, (void*)0);
	
	return 0;
}

extern void __VERIFIER_error();

int x = 0;

int main()
{
	int i = 0;
	for(; i < 5; ++i) {
		if(x > 3) {
			ERROR: __VERIFIER_error();
		}
		x = x + 1;
	}
	return 0;
}

typedef unsigned long pthread_t;

int x = 0;

void t1()
{
	x = 1;
//	int r1 = x + 1;
}

void t2()
{
//	x = 2;
	int r2 = x + 2;
}

void t3()
{
	int r3 = x + 3;
}

int main()
{
	pthread_t tid1, tid2, tid3;
	
	pthread_create(&tid1, 0, t1, 0);
	pthread_create(&tid2, 0, t2, 0);
	pthread_create(&tid3, 0, t3, 0);
	
	pthread_join(tid1, 0);
	pthread_join(tid2, 0);
	pthread_join(tid3, 0);
	
	return 0;
}

typedef unsigned long pthread_t;

extern void reach_error();

int x = 2;

void t1()
{
	int l1 = 1;
	if(x > 3)
		reach_error();
}

void t2()
{
	x = x + 1;
	x = x + 2;
}

int main()
{
	pthread_t tid1, tid2;
	
	pthread_create(&tid1, 0, t1, 0);
	pthread_create(&tid2, 0, t2, 0);
	
	pthread_join(tid1, 0);
	pthread_join(tid2, 0);
	
	return 0;
}

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


typedef unsigned long pthread_t;

extern void reach_error();

int x = -1, y = 2;

void t1(void *arg)
{
	x = x + 1;
}


int main()
{
	pthread_t tid1, tid2, tid3;
	
	pthread_create(&tid1, 0, t1, 0);
	pthread_create(&tid2, 0, t1, 0);
	pthread_create(&tid3, 0, t1, 0);

	pthread_join(tid1, 0);
	pthread_join(tid2, 0);
	pthread_join(tid3, 0);
	
	return 0;
}

typedef unsigned long pthread_t;

extern void reach_error();

int x = -1, y = 2;

void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}

void t1(void *arg)
{
	assume_abort_if_not(x + y == -1);
}

void t2(void *arg)
{
	assume_abort_if_not(y + x == 2);
}

int main()
{
	pthread_t tid1, tid2;
	
	pthread_create(&tid1, 0, t1, 0);
	pthread_create(&tid2, 0, t2, 0);

//	pthread_join(tid1, 0);
//	pthread_join(tid2, 0);
	
	return 0;
}

