#include<stdio.h>
#include<stdlib.h>
#include<string.h>

void write(int val_type, int val) {
	printf("%d\n", val);
	asm("movl $0, %ebx");
}

int read() {
  int val;
  printf("> ");
  scanf("%d", &val);

  int type_ret;
  asm("movl $0, %ebx");

  return val;
}

extern size_t strlen(const char *str);

size_t strlen_2(int str_type, const char *str) {
    int result = strlen(str);

    int type_ret;
    asm("movl $0, %ebx");

    return result;
}

extern char * strdup(const char *str);

char *strdup_2(int str_type, const char *str) {
    char *result = strdup(str);

    int type_ret;
    asm("movl $0, %ebx");

    return result;
}

extern int strcmp(const char *str1, const char *str2);

int strcmp_4(int str1_type, const char *str1, int str2_type, const char *str2) {
    int result = strcmp(str1, str2);

    int type_ret;
    asm("movl $0, %ebx");

    return result;
}

char strget(int s_type, const char *s, int i_type, size_t i) {
    int result = s[i];

    int type_ret;
    asm("movl $0, %ebx");

    return result;
}

int strset(int s_type, char *s, int i_type, size_t i, int c_type, char c) {
    s[i] = c;

    int type_ret;
    asm("movl $0, %ebx");

    return 0;
}

char *strsub(int s_type, const char *s, int from_type, size_t from, int n_type, size_t n) {
    char* result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, s + from, n);

    int type_ret;
    asm("movl $0, %ebx");

    return result;
}

char *strcat_4(int l_type, char *l, int r_type, const char *r) {
    size_t n1 = strlen(l);
    size_t n2 = strlen(r);
    size_t n = n1 + n2;
    char * result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, l, n1);
    memcpy(result + n1, r, n2);

    int type_ret;
    asm("movl $0, %ebx");

    return result;
}

char *strmake(int n_type, size_t n, int c_type, char c) {
    char *result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memset(result, c, n);

    int type_ret;
    asm("movl $0, %ebx");

    return result;
}

const int type_int = 0;
const int type_array = 1;

const int header_size = 3;

int _arr_size(int arr_type, int n_items) {
    int result_ints = header_size;
    result_ints += (arr_type == type_int ? n_items : n_items * 2);
    if (result_ints % 4 != 0) {
        result_ints += 4 - (result_ints % 4);
    }
    return result_ints * sizeof(int);
}

int *arrmake(int n_type, size_t n, int init_type, int init) {
    int arr_size = _arr_size(type_int, n);
    int* result = (int*) malloc(arr_size);
    result[0] = type_int;
    result[1] = 0; // ref count
    result[2] = n; // size
    for (int i = 0; i <= n; i++) {
        result[i + header_size] = init;
    }

    int type_ret;
    asm("movl $1, %ebx");

    return result;
}

int *arrset(int array_type, int *array, int index_type, int index, int value_type, int value);

int _value_index(int arr_type, int index) {
    int offset = index;
    if (arr_type == type_array) {
        offset *= 2;
    }
    return header_size + offset;
}

int _value_from(int arr_type, int* arr, int index) {
    int value_index = _value_index(arr_type, index);
    return arr[value_index];
}

int _type_from(int arr_type, int* arr, int index) {
    if (arr_type == type_int) {
        return type_int;
    }
    int type_index = _value_index(arr_type, index) + 1;
    return arr[type_index];
}

int *Arrmake(int n_type, size_t n, int init_type, int init) {
    int arr_size = _arr_size(type_array, n);
    int* result = (int*) malloc(arr_size);
    result[0] = type_array;
    result[1] = 0; // ref count
    result[2] = n; // size
    for (int i = 0; i <= n; i++) {
        arrset(type_array, result, type_int, i, init_type, init);
    }

    int type_ret;
    asm("movl $1, %ebx");

    return result;
}

int arrget(int array_type, int *array, int index_type, int index) {
    int arr_type = array[0];
    int item = _value_from(arr_type, array, index);
    int type = _type_from(arr_type, array, index);

    int type_ret;
    asm("movl %0, %%ebx"
        :
        :"r"(type));

    return item;
}

void ref_increase(int array_type, int* array);
void ref_decrease(int array_type, int* array);

int *arrset(int array_type, int *array, int index_type, int index, int value_type, int value) {
    int arr_type = array[0];

    int old_value = _value_from(arr_type, array, index);
    int old_type = _type_from(arr_type, array, index);
    if (old_type == value_type && old_value == value) {
        asm("movl $0, %ebx");
        return 0;
    }

    if (old_type == type_array) {
	    ref_decrease(type_array, (int*) old_value);
    }

    int item_index = _value_index(arr_type, index);
    array[item_index] = value;

    if (arr_type == type_array) {
        array[item_index + 1] = value_type;
	    ref_increase(value_type, (int*) array[item_index]);
    }

    asm("movl $0, %ebx");
    return 0;
}

int arrlen(int array_type, int *array) {
    int result = array[2];

    asm("movl $0, %ebx");
    return result;
}

void ref_decrease(int array_type, int* array) {
    if (array_type != type_array) {
        return;
    }

    if (array[0] == -1) { // array is already in the procedure of ref operation
        asm("movl $0, %ebx");
        return;
    }

    int arr_type = array[0];
    array[0] = -1;

    int ref_count = array[1];
    int size = array[2];

    int new_ref_count = ref_count - 1;
    if (new_ref_count == 0) {
        if (arr_type == type_array) {
            for (int i = 0; i < size; ++i) {
                int* value = (int*) _value_from(arr_type, array, i);
                int item_type = _type_from(arr_type, array, i);
                ref_decrease(item_type, value);
            }
		}

        free(array);
    } else {
        array[1] = new_ref_count;
        array[0] = arr_type;
    }
    asm("movl $0, %ebx");
}

void ref_increase(int array_type, int* array) {
    if (array_type != type_array) {
        return;
    }

    int ref_count = array[1];
    int new_ref_count = ref_count + 1;
    array[1] = new_ref_count;

    asm("movl $0, %ebx");
}